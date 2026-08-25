package com.dimalab.storymodengine.api.event;

/**
 * Opt-in cancellation capability — only an event that implements this can be cancelled; {@link
 * Event} itself carries no such method. See {@link EventBus}'s Javadoc for the exact, documented
 * cancellation rule this engine uses (a deliberate simplification of Forge's per-listener
 * {@code receiveCanceled} behavior, not a copy of it).
 */
public interface CancellableEvent extends Event {

    boolean isCancelled();

    void setCancelled(boolean cancelled);
}
