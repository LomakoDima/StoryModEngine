package com.dimalab.storymodengine.common.flow;

/**
 * A small, reusable tick-counter — the one primitive {@code Wait}, {@code EventWaiter}, {@code
 * Task}, and {@code SubFlow} can each optionally hold to bound how long they're willing to stay
 * {@code RUNNING}, without duplicating counter logic in each of them and without ever blocking
 * execution (it's purely a counter, ticked cooperatively by whichever node owns it — the same
 * "just another {@code onTick} check" every other node already uses).
 */
public final class Timeout {

    /** No timeout — {@link #tick()} never expires. */
    public static final Timeout NONE = new Timeout(-1);

    private final int ticks;
    private int elapsed;

    private Timeout(int ticks) {
        this.ticks = ticks;
    }

    public static Timeout ticks(int ticks) {
        if (ticks < 0) {
            throw new IllegalArgumentException("ticks must be >= 0");
        }
        return new Timeout(ticks);
    }

    public boolean isEnabled() {
        return ticks >= 0;
    }

    /** Advances by one tick; returns {@code true} exactly once, the tick this timeout expires. */
    public boolean tick() {
        return isEnabled() && ++elapsed == ticks;
    }
}
