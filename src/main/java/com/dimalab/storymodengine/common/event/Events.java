package com.dimalab.storymodengine.common.event;

import com.dimalab.storymodengine.api.event.Event;
/**
 * The engine-wide default {@link EventBus} — the API a mod author actually calls day to day:
 *
 * <pre>{@code
 * Events.post(new QuestCompleted(questId));
 * }</pre>
 *
 * A dedicated {@link EventBus} instance can't itself expose a static {@code post(Event)} (Java
 * forbids a static and instance method sharing one signature on the same class, and {@link
 * EventBus} needs real instance methods to stay unit-testable in isolation — see its Javadoc) — this
 * class is that static entry point, exactly the same shape as {@code Network} (a static facade over
 * per-packet {@code SimpleChannel}s) and {@code Capabilities} (a static facade over {@code
 * CapabilityStorage}) elsewhere in this engine. {@link #bus()} returns the underlying instance for
 * anything that needs it directly (mainly {@code EventListenerDiscovery}).
 */
public final class Events {

    private static final EventBus GLOBAL = new EventBus();

    private Events() {
    }

    /** The shared engine-wide {@link EventBus} instance this facade delegates to. */
    public static EventBus bus() {
        return GLOBAL;
    }

    public static void post(Event event) {
        GLOBAL.post(event);
    }

    public static int register(Object target) {
        return GLOBAL.register(target);
    }

    public static void unregister(Object target) {
        GLOBAL.unregister(target);
    }
}
