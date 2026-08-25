package com.dimalab.storymodengine.api.event;

/**
 * Listener execution order — {@link #HIGHEST} runs first, {@link #LOWEST} runs last, {@link
 * #NORMAL} is the {@link SubscribeEvent#priority()} default. Within the same priority, listeners
 * run in registration order (see {@link EventBus}). No {@code MONITOR} tier: nothing in this
 * engine yet needs an "always runs last, never affects anything" observer distinct from
 * {@link #LOWEST}, and adding one without a concrete use is exactly the premature complexity the
 * task asked to avoid.
 */
public enum EventPriority {
    HIGHEST,
    HIGH,
    NORMAL,
    LOW,
    LOWEST
}
