package com.dimalab.storymodengine.client.model.animator;

/** How a clip's local time wraps once it reaches its ends — see {@link ClipPlayback}. */
public enum AnimationPlayMode {
    /** Clamps at the end and stays there. */
    ONCE,
    /** Wraps back to the start. */
    LOOP,
    /** Clamps at the end, forever — unlike {@link #ONCE}, never reports {@code ended}. */
    CLAMP_FOREVER,
    /** Bounces back and forth between the start and the end. */
    PING_PONG
}
