package com.dimalab.storymodengine.common.flow.node;

import com.dimalab.storymodengine.api.flow.Evaluator;
import com.dimalab.storymodengine.common.flow.FlowContext;
import com.dimalab.storymodengine.common.logging.EngineLog;

/**
 * Checks {@link FlowContext} (world/capability state) and completes if true, fails if false — the
 * simple gate used directly inside a {@code Sequence} in the required example
 * ("Story Points >= 1?"). For choosing between two genuinely different paths (not just "stop here
 * on false"), use {@code Branch} instead. Built via {@code Flow.condition(...)}.
 */
public final class Condition extends Node {

    private final Evaluator<Boolean> evaluator;

    public Condition(Evaluator<Boolean> evaluator) {
        this.evaluator = evaluator;
    }

    @Override
    protected void onStart(FlowContext context) {
        boolean result;
        try {
            result = Boolean.TRUE.equals(evaluator.evaluate(context));
        } catch (Exception e) {
            EngineLog.channel("Flow").error("Condition threw, treating as failure: {}", e.toString());
            fail();
            return;
        }
        if (result) {
            complete();
        } else {
            fail();
        }
    }
}
