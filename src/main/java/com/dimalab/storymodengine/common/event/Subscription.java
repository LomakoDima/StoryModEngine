package com.dimalab.storymodengine.common.event;

/** A handle for a listener registered via {@link EventBus#subscribe} — call {@link #unsubscribe()} exactly once when done listening. */
@FunctionalInterface
public interface Subscription {

    void unsubscribe();
}
