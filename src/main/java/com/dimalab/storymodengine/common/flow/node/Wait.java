package com.dimalab.storymodengine.common.flow.node;

import com.dimalab.storymodengine.common.flow.FlowContext;

/**
 * Completes after a fixed number of engine ticks — non-blocking by construction, it's just another
 * {@link #onTick} counter, exactly like every other node's own tick-driven state. Built via
 * {@code Flow.wait(int)}.
 *
 * <p><b>Documented v1 limitation</b>, the same category as {@code Action}'s: the elapsed-tick
 * counter is not part of {@code NodeSnapshot} and so isn't persisted. Restoring a flow parked mid-
 * {@code Wait} calls {@link #onStart} again (see {@code Node#restore}'s leaf case) and starts the
 * wait over from zero rather than resuming the remaining duration — genuinely fixing that needs an
 * idempotency/Checkpoint mechanism this version deliberately doesn't build.
 */
public final class Wait extends Node {

    private final int ticks;
    private int elapsed;

    public Wait(int ticks) {
        if (ticks < 0) {
            throw new IllegalArgumentException("ticks must be >= 0");
        }
        this.ticks = ticks;
    }

    @Override
    protected void onStart(FlowContext context) {
        elapsed = 0;
        if (ticks == 0) {
            complete();
        }
    }

    @Override
    protected void onTick(FlowContext context) {
        if (++elapsed >= ticks) {
            complete();
        }
    }
}
