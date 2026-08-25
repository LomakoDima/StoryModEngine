package com.dimalab.storymodengine.common.trigger;

/**
 * How many times a {@link Trigger} may fire for a given firing context (a specific player for a
 * {@code LOCATION}/player-scoped {@code EVENT} trigger, or the server as a whole for a {@code TIME}
 * trigger or a global-scoped {@code EVENT} trigger — see {@link Trigger}'s own doc on scope
 * inference). Set via {@link Trigger.Builder#once()}/{@link Trigger.Builder#repeat()}; {@code
 * REPEAT} is the default.
 */
public enum TriggerPolicy {
    /** Fires once for a given context, then never again for that same context. */
    ONCE,
    /** May fire again every time its condition is met. */
    REPEAT
}
