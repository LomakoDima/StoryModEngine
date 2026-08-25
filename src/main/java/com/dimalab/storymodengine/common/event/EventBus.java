package com.dimalab.storymodengine.common.event;

import com.dimalab.storymodengine.api.event.CancellableEvent;
import com.dimalab.storymodengine.api.event.SubscribeEvent;
import com.dimalab.storymodengine.api.event.Event;
import com.dimalab.storymodengine.api.event.EventPriority;
import com.dimalab.storymodengine.common.logging.EngineLog;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * The real event dispatch engine — an independent, instantiable object (not a static singleton)
 * specifically so it can be unit-tested in isolation with no shared global state:
 * {@code new EventBus()}, register some listeners, post some events, assert. {@link Events} is the
 * thin static facade over one shared global instance that mod authors actually call day to day.
 *
 * <p>Not related to Forge's {@code net.minecraftforge.eventbus.api.IEventBus} or its internal
 * {@code net.minecraftforge.eventbus.EventBus} impl — same concept, deliberately independent
 * engine-level abstraction, in a different package. Nothing here extends, implements, or otherwise
 * touches those types; {@code SimpleChannel}, {@code FriendlyByteBuf}, {@code LazyOptional},
 * {@code CapabilityProvider}, and Forge's own event types never appear in this class's API.
 *
 * <h2>Registration</h2>
 * {@link #register(Object)} mirrors real Forge {@code EventBus} semantics (used here as an
 * architectural reference, not an implementation dependency): pass a {@code Class<?>} to register
 * only its {@code static} {@code @SubscribeEvent} methods, or pass an instance to register both its
 * static and instance {@code @SubscribeEvent} methods, bound to that instance. Every {@code static}
 * listener across a mod's own jar is found and registered automatically by
 * {@code EventListenerDiscovery} (wired into {@code EngineBootstrap.init}) calling exactly this
 * same method — there is one validation/binding path for both automatic and manual registration,
 * not two. An instance listener always needs one explicit {@link #register(Object)} call, since the
 * engine has no way to construct an arbitrary listener object itself.
 *
 * <h2>Dispatch order</h2>
 * Listeners run {@link EventPriority#HIGHEST} first, {@link EventPriority#LOWEST} last; within the
 * same priority, in registration order (a monotonic counter, not reflection/hash order — see
 * {@link #computeDispatchOrder}). The ordered list for a given <em>concrete</em> event class is
 * computed once, lazily, on its first {@link #post(Event)} — walking that class's full superclass
 * and interface chain up to {@link Event} — and cached; {@link #register}/{@link #unregister}
 * simply drop the whole cache (registration is cold/rare, {@code post} is hot, so a full clear is
 * simpler than incremental invalidation and cannot be wrong). This is what makes {@code post} O(1)
 * amortized rather than a listener scan every call.
 *
 * <h2>Parent-event dispatch</h2>
 * A listener declared for a supertype receives every subtype post, including through interfaces —
 * {@code @SubscribeEvent void onAny(Event e)} receives literally everything ever posted;
 * {@code @SubscribeEvent void onPlayer(PlayerEvent e)} receives a posted {@code PlayerJoinEvent}
 * too, if {@code PlayerJoinEvent extends PlayerEvent}. This only works through real class/interface
 * inheritance — a {@code record} cannot {@code extends} another type, so a leaf event with no
 * family stays a plain {@code record implements Event}, while an extensible event family
 * ({@code class PlayerEvent implements Event}, {@code class PlayerJoinEvent extends PlayerEvent})
 * uses ordinary classes. Both styles are fully supported side by side.
 *
 * <h2>Cancellation</h2>
 * A documented, deliberate simplification of Forge's own behavior (the task explicitly asked not to
 * copy it blindly): once a {@link CancellableEvent#isCancelled()} posted event becomes cancelled,
 * dispatch stops outright — no further listener runs, regardless of its priority. There is no
 * Forge-style {@code receiveCanceled} opt-in for an "always-runs observer"; nothing in this engine
 * needs one yet, and adding that flag later (one more {@code @SubscribeEvent} attribute) is a small,
 * isolated, backward-compatible extension if a real use case appears.
 *
 * <h2>Error isolation</h2>
 * A listener that throws is caught, logged via {@code EngineLog.channel("Events")} at
 * {@code warn}, and dispatch continues to the next listener — one broken handler never stops the
 * rest, and never crashes the game.
 *
 * <h2>Threading</h2>
 * {@link #post(Event)} is fully synchronous on the calling thread — it never hops threads, never
 * queues, never schedules. A caller on a network thread that needs an event handled on the main
 * thread uses the network system's own existing mechanism for that:
 * {@code context.enqueue(() -> Events.post(event))} (see {@code PacketContext#enqueue}) — the bus
 * itself stays deliberately dumb about threads. Concurrent {@link #register}/{@link #unregister}/
 * {@link #post} calls from different threads are safe (backed by {@link ConcurrentHashMap}); a
 * listener registered concurrently with an in-flight {@code post} of the exact same event class may
 * or may not be included in that one in-flight call — never a crash or corrupt state, just a benign
 * race not worth eliminating for a game loop that's overwhelmingly single-threaded in practice.
 */
public final class EventBus {

    private static final MethodHandle CONSUMER_ACCEPT;

    static {
        try {
            CONSUMER_ACCEPT = MethodHandles.publicLookup().findVirtual(
                    Consumer.class, "accept", MethodType.methodType(void.class, Object.class));
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final Map<Class<?>, List<RegisteredListener>> byEventType = new ConcurrentHashMap<>();
    private final Map<Class<?>, List<RegisteredListener>> dispatchCache = new ConcurrentHashMap<>();
    private final Object structuralLock = new Object();
    private final AtomicLong sequence = new AtomicLong();

    /**
     * Registers every {@code @SubscribeEvent} method {@code target} exposes. {@code target} may be
     * a {@code Class<?>} (only its {@code static} methods are registered) or any other object (both
     * its {@code static} and instance methods are registered, instance ones bound to it).
     *
     * @return how many listeners were newly bound (0 if {@code target} declares none, or all were
     * rejected by validation — see the per-listener {@code warn} logs for why)
     */
    public int register(Object target) {
        Objects.requireNonNull(target, "target");
        Class<?> ownerClass = (target instanceof Class<?> c) ? c : target.getClass();
        Object invokeTarget = (target instanceof Class<?>) ? null : target;
        Object ownerKey = (target instanceof Class<?>) ? ownerClass : target;

        int count = 0;
        for (Method method : ownerClass.getDeclaredMethods()) {
            if (!method.isAnnotationPresent(SubscribeEvent.class)) {
                continue;
            }
            boolean isStatic = Modifier.isStatic(method.getModifiers());
            if (!isStatic && invokeTarget == null) {
                // A bare Class<?> was passed — only its static listeners apply. This instance
                // method will be found again if/when the mod author registers a real instance.
                continue;
            }
            EventPriority priority = method.getAnnotation(SubscribeEvent.class).priority();
            if (bind(ownerClass, method, isStatic ? null : invokeTarget, priority, ownerKey)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Registers a plain lambda listener for exactly one event type — the dynamic counterpart to
     * {@code @SubscribeEvent} for code that only knows what to listen for at runtime (used by
     * {@code flow.node.EventWaiter}). Reuses the exact same dispatch/priority/cancellation/cache
     * machinery {@code post} already runs: the handler is wrapped in a {@code MethodHandle} bound
     * to {@code Consumer.accept} with the identical {@code (Event)->void} shape a {@code
     * @SubscribeEvent} method already resolves to, so nothing about {@link #post} changes to
     * support this. Call {@link Subscription#unsubscribe()} exactly once when done listening —
     * there is no automatic cleanup.
     */
    public <E extends Event> Subscription subscribe(Class<E> eventType, EventPriority priority, Consumer<E> handler) {
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(handler, "handler");

        MethodHandle invoker = CONSUMER_ACCEPT.bindTo(handler).asType(MethodType.methodType(void.class, Event.class));
        Object owner = new Object();
        RegisteredListener listener = new RegisteredListener(
                eventType, priority, sequence.incrementAndGet(), invoker, "lambda:" + eventType.getSimpleName(), owner);

        synchronized (structuralLock) {
            byEventType.computeIfAbsent(eventType, t -> new ArrayList<>()).add(listener);
            dispatchCache.clear();
        }
        EngineLog.channel("Events").debug("Subscribed lambda listener for {} (priority={})", eventType.getSimpleName(), priority);
        return () -> unregister(owner);
    }

    /** Removes every listener previously bound to {@code target} (by identity, or by {@code Class} for a static-only registration). */
    public void unregister(Object target) {
        Objects.requireNonNull(target, "target");
        Object ownerKey = target;
        synchronized (structuralLock) {
            for (List<RegisteredListener> listeners : byEventType.values()) {
                listeners.removeIf(l -> l.owner() == ownerKey);
            }
            dispatchCache.clear();
        }
        EngineLog.channel("Events").debug("Unregistered listeners owned by {}", describe(target));
    }

    /**
     * Dispatches {@code event} to every listener registered for its runtime type or any of that
     * type's supertypes, highest priority first. See the class Javadoc for the exact cancellation,
     * error-isolation, and threading contracts.
     */
    public void post(Event event) {
        Objects.requireNonNull(event, "event");
        Class<?> eventClass = event.getClass();
        List<RegisteredListener> listeners = dispatchCache.computeIfAbsent(eventClass, this::computeDispatchOrder);

        if (listeners.isEmpty()) {
            EngineLog.channel("Events").trace("Posted {} — no listeners", eventClass.getSimpleName());
            return;
        }

        boolean cancellable = event instanceof CancellableEvent;
        int ran = 0;
        for (RegisteredListener listener : listeners) {
            if (cancellable && ((CancellableEvent) event).isCancelled()) {
                break;
            }
            try {
                listener.invoker().invokeExact(event);
                ran++;
            } catch (Throwable t) {
                EngineLog.channel("Events").warn(
                        "Listener {} threw handling {}, continuing: {}", listener.debugName(), eventClass.getSimpleName(), t.toString());
            }
        }
        EngineLog.channel("Events").trace("Posted {} → {}/{} listener(s) ran", eventClass.getSimpleName(), ran, listeners.size());
    }

    private boolean bind(Class<?> ownerClass, Method method, Object invokeTarget, EventPriority priority, Object ownerKey) {
        Class<?>[] params = method.getParameterTypes();
        if (params.length != 1) {
            EngineLog.channel("Events").warn(
                    "Skipping @SubscribeEvent {}.{} — must declare exactly one parameter",
                    ownerClass.getSimpleName(), method.getName());
            return false;
        }
        Class<?> eventType = params[0];
        if (!Event.class.isAssignableFrom(eventType)) {
            EngineLog.channel("Events").warn(
                    "Skipping @SubscribeEvent {}.{} — parameter {} does not implement Event",
                    ownerClass.getSimpleName(), method.getName(), eventType.getSimpleName());
            return false;
        }
        if (method.getReturnType() != void.class) {
            EngineLog.channel("Events").warn(
                    "Skipping @SubscribeEvent {}.{} — listener methods must return void",
                    ownerClass.getSimpleName(), method.getName());
            return false;
        }

        MethodHandle invoker;
        try {
            method.setAccessible(true);
            MethodHandle raw = MethodHandles.lookup().unreflect(method);
            invoker = invokeTarget == null
                    ? raw.asType(MethodType.methodType(void.class, Event.class))
                    : raw.bindTo(invokeTarget).asType(MethodType.methodType(void.class, Event.class));
        } catch (Exception e) {
            EngineLog.channel("Events").warn(
                    "Skipping @SubscribeEvent {}.{} — not invocable: {}",
                    ownerClass.getSimpleName(), method.getName(), e.toString());
            return false;
        }

        RegisteredListener listener = new RegisteredListener(
                eventType, priority, sequence.incrementAndGet(), invoker,
                ownerClass.getSimpleName() + "." + method.getName(), ownerKey);

        synchronized (structuralLock) {
            byEventType.computeIfAbsent(eventType, t -> new ArrayList<>()).add(listener);
            dispatchCache.clear();
        }
        EngineLog.channel("Events").debug(
                "Registered listener {} ({}, priority={})", listener.debugName(), eventType.getSimpleName(), priority);
        return true;
    }

    private List<RegisteredListener> computeDispatchOrder(Class<?> eventClass) {
        Set<Class<?>> supertypes = new LinkedHashSet<>();
        collectEventSupertypes(eventClass, supertypes);

        List<RegisteredListener> collected = new ArrayList<>();
        for (Class<?> type : supertypes) {
            List<RegisteredListener> exact = byEventType.get(type);
            if (exact != null) {
                collected.addAll(exact);
            }
        }
        collected.sort(Comparator.comparing(RegisteredListener::priority).thenComparingLong(RegisteredListener::sequence));
        return List.copyOf(collected);
    }

    /** Walks {@code type}'s superclass and interface chain, collecting every {@link Event}-assignable supertype (including itself). */
    private static void collectEventSupertypes(Class<?> type, Set<Class<?>> out) {
        if (type == null || !Event.class.isAssignableFrom(type) || !out.add(type)) {
            return;
        }
        collectEventSupertypes(type.getSuperclass(), out);
        for (Class<?> iface : type.getInterfaces()) {
            collectEventSupertypes(iface, out);
        }
    }

    private static String describe(Object target) {
        return target instanceof Class<?> c ? c.getSimpleName() : target.getClass().getSimpleName() + "@instance";
    }

    private record RegisteredListener(
            Class<?> declaredEventType,
            EventPriority priority,
            long sequence,
            MethodHandle invoker,
            String debugName,
            Object owner) {
    }
}
