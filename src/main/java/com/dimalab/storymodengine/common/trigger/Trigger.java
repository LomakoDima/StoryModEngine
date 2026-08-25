package com.dimalab.storymodengine.common.trigger;

import com.dimalab.storymodengine.api.event.Event;
import com.dimalab.storymodengine.common.flow.Flow;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.function.Function;

/**
 * <b>Trigger answers "when"; {@link Flow} answers "what."</b> A {@code Trigger} owns exactly one
 * detection mechanism (a location region, a game-time window, or an existing engine {@link Event})
 * and, once that condition is met, hands a plain {@link Flow} to the already-existing {@code
 * FlowManager} — see {@code TriggerSystem}. There is no separate Trigger execution engine: firing a
 * trigger is nothing but one {@code FlowManager.start(flowId, player)} call, exactly like {@code
 * QuestSystem}/{@code DialogueSystem} already do for their own compiled flows.
 *
 * <pre>{@code
 * @AutoTrigger
 * public static final Trigger VILLAGE_ENTRY = Trigger.location("village_entry")
 *         .radius(new BlockPos(100, 64, 200), 5)
 *         .whenEntered()
 *         .once()
 *         .run(Flow.sequence(...));
 * }</pre>
 *
 * <p><b>Scope is inferred, never a builder call</b> — a {@code LOCATION} trigger and an {@code
 * EVENT} trigger whose event type exposes a {@code player()} (or the caller's own explicit
 * extractor via {@link Builder#on(Class, Function)}) fire per player: {@link TriggerPolicy#ONCE}
 * means "once per player." A {@code TIME} trigger, and an {@code EVENT} trigger whose event carries
 * no identifiable player, fire once for every currently-online player: {@code ONCE} there means
 * "once for the server, ever" — whichever players happen to be online at that moment each get an
 * independent {@link Flow} instance, a player who wasn't online never does. This mirrors how a
 * one-time world event actually reads in-game, and needs no scope parameter for a mod author to
 * get wrong.
 *
 * <p><b>Not related to</b> {@code cinematic.Trigger} (an unrelated, narrower type — "at cutscene
 * tick N, fire timeline cue X"). Different package, no collision, but don't import both into the
 * same file without qualifying one.
 */
public final class Trigger {

    /**
     * How wide a window {@link #matchesTime} accepts around an exact {@link Builder#at} tick.
     * {@code TIME} triggers are polled, not event-driven (no Forge "game time reached X" event
     * exists) — see {@code integration.TriggerTickBridge}, which polls at exactly this interval, so
     * an exact single-tick target could otherwise be stepped over entirely.
     */
    public static final int TIME_CHECK_WINDOW_TICKS = 20;

    private final ResourceLocation id;
    private final TriggerKind kind;

    // location
    private final Vec3 center;
    private final double radius;
    private final AABB aabb;
    private final boolean whenExited;

    // time
    private final int atTime;
    private final int fromTime;
    private final int toTime;

    // event
    private final Class<? extends Event> eventType;
    private final Function<Object, ServerPlayer> playerExtractor;

    // shared
    private final TriggerPolicy policy;
    private final boolean persistent;
    private final Flow flow;

    private volatile boolean enabled = true;

    private Trigger(Builder builder) {
        this.id = builder.id;
        this.kind = builder.kind;
        this.center = builder.center;
        this.radius = builder.radius;
        this.aabb = builder.aabb;
        this.whenExited = builder.whenExited;
        this.atTime = builder.atTime;
        this.fromTime = builder.fromTime;
        this.toTime = builder.toTime;
        this.eventType = builder.eventType;
        this.playerExtractor = builder.playerExtractor;
        this.policy = builder.policy;
        this.persistent = builder.persistent;
        this.flow = Objects.requireNonNull(builder.flow, "Trigger '" + builder.id + "': .run(flow) was never called");
    }

    public static Builder location(String id) {
        return new Builder(id, TriggerKind.LOCATION);
    }

    public static Builder time(String id) {
        return new Builder(id, TriggerKind.TIME);
    }

    public static Builder event(String id) {
        return new Builder(id, TriggerKind.EVENT);
    }

    public ResourceLocation id() {
        return id;
    }

    public TriggerKind kind() {
        return kind;
    }

    public TriggerPolicy policy() {
        return policy;
    }

    public boolean persistent() {
        return persistent;
    }

    public boolean whenExited() {
        return whenExited;
    }

    public Class<? extends Event> eventType() {
        return eventType;
    }

    /** Non-null only for an {@code EVENT} trigger whose player could be identified — see the class doc's scope-inference note. */
    public Function<Object, ServerPlayer> playerExtractor() {
        return playerExtractor;
    }

    public Flow flow() {
        return flow;
    }

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** {@code LOCATION} only — whether {@code pos} falls inside this trigger's region. */
    public boolean containsPosition(Vec3 pos) {
        if (aabb != null) {
            return aabb.contains(pos);
        }
        double dx = pos.x - center.x;
        double dy = pos.y - center.y;
        double dz = pos.z - center.z;
        return dx * dx + dy * dy + dz * dz <= radius * radius;
    }

    /** {@code TIME} only — whether {@code dayTime} (already {@code % 24000}) falls in this trigger's configured point/range. */
    public boolean matchesTime(int dayTime) {
        if (atTime >= 0) {
            int windowEnd = (atTime + TIME_CHECK_WINDOW_TICKS) % 24000;
            return inWindow(dayTime, atTime, windowEnd);
        }
        if (fromTime >= 0) {
            return inWindow(dayTime, fromTime, toTime);
        }
        return false;
    }

    private static boolean inWindow(int t, int from, int to) {
        return from <= to ? (t >= from && t <= to) : (t >= from || t <= to);
    }

    @Override
    public String toString() {
        return "Trigger{" + id + " " + kind + " " + policy + (persistent ? " persistent" : "") + (enabled ? "" : " disabled") + "}";
    }

    public static final class Builder {

        private final ResourceLocation id;
        private final TriggerKind kind;

        private Vec3 center;
        private double radius;
        private AABB aabb;
        private boolean whenExited;

        private int atTime = -1;
        private int fromTime = -1;
        private int toTime = -1;

        private Class<? extends Event> eventType;
        private Function<Object, ServerPlayer> playerExtractor;

        private TriggerPolicy policy = TriggerPolicy.REPEAT;
        private boolean persistent;
        private Flow flow;

        private Builder(String id, TriggerKind kind) {
            this.id = id.contains(":") ? new ResourceLocation(id) : new ResourceLocation("storymodengine", id);
            this.kind = kind;
        }

        public Builder radius(Vec3 center, double radius) {
            this.center = center;
            this.radius = radius;
            this.aabb = null;
            return this;
        }

        public Builder radius(BlockPos center, double radius) {
            return radius(Vec3.atCenterOf(center), radius);
        }

        public Builder aabb(BlockPos min, BlockPos max) {
            this.aabb = new AABB(min.getX(), min.getY(), min.getZ(), max.getX() + 1, max.getY() + 1, max.getZ() + 1);
            this.center = null;
            return this;
        }

        public Builder whenEntered() {
            this.whenExited = false;
            return this;
        }

        public Builder whenExited() {
            this.whenExited = true;
            return this;
        }

        /** An exact game-time tick (0–23999) — matched within a small window since this is polled, not event-driven; see {@link Trigger#matchesTime}. */
        public Builder at(int dayTime) {
            this.atTime = dayTime;
            this.fromTime = -1;
            return this;
        }

        /** A game-time range, wrapping past midnight if {@code fromTime > toTime} (e.g. {@code between(13000, 23000)} for "night"). */
        public Builder between(int fromTime, int toTime) {
            this.fromTime = fromTime;
            this.toTime = toTime;
            this.atTime = -1;
            return this;
        }

        /** Subscribes to {@code eventType} on the existing {@code EventBus}. The player (for per-player scope/ONCE-tracking) is looked up via a {@code player()} accessor if one exists on {@code eventType} — see {@link #on(Class, Function)} for events that don't follow that convention. */
        public <E extends Event> Builder on(Class<E> eventType) {
            this.eventType = eventType;
            this.playerExtractor = EventPlayerExtractor.tryFind(eventType);
            return this;
        }

        /** Same as {@link #on(Class)}, but with an explicit player extractor — for an event whose player accessor doesn't follow the {@code player()} convention (e.g. {@code EntityKilledEvent.killer()}), or that carries no player at all (pass {@code null} to force global scope). */
        @SuppressWarnings("unchecked")
        public <E extends Event> Builder on(Class<E> eventType, Function<E, ServerPlayer> playerExtractor) {
            this.eventType = eventType;
            this.playerExtractor = playerExtractor == null ? null : event -> playerExtractor.apply((E) event);
            return this;
        }

        public Builder once() {
            this.policy = TriggerPolicy.ONCE;
            return this;
        }

        public Builder repeat() {
            this.policy = TriggerPolicy.REPEAT;
            return this;
        }

        /** Whether "already fired" survives a reconnect/restart — via the existing {@code @Capability} system, not a new persistence framework. {@code false} (in-memory only) by default. */
        public Builder persistent() {
            this.persistent = true;
            return this;
        }

        /** The {@link Flow} started once this trigger fires — an independent instance per firing, handed straight to the existing {@code FlowManager}. */
        public Trigger run(Flow flow) {
            this.flow = flow;
            return new Trigger(this);
        }
    }
}
