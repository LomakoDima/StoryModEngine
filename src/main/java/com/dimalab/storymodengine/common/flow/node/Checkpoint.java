package com.dimalab.storymodengine.common.flow.node;

import com.dimalab.storymodengine.common.flow.FlowContext;
import com.dimalab.storymodengine.common.logging.EngineLog;

/**
 * A self-documenting "this is a safe point to resume from" marker — functionally a no-op that
 * completes instantly. Built via {@code Flow.checkpoint()}.
 *
 * <p>Does not change how often persistence actually happens: {@code FlowManager} already persists
 * on every node-state transition, not "blindly every tick" — reaching a {@code Checkpoint} is
 * itself just one more such transition. What this class adds is intent, not frequency: a {@code
 * Flow} author can mark the specific points they're confident are safe to resume from, which is a
 * real hook for a future mode that persists less often than every transition, without that mode
 * needing to exist yet.
 */
public final class Checkpoint extends Node {

    @Override
    protected void onStart(FlowContext context) {
        EngineLog.channel("Flow").trace("Checkpoint reached");
        complete();
    }
}
