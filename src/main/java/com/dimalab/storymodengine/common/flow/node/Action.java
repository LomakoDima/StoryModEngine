package com.dimalab.storymodengine.common.flow.node;

import com.dimalab.storymodengine.common.flow.FlowContext;
import com.dimalab.storymodengine.common.logging.EngineLog;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Runs some effect once and completes — no async, no {@code Task}/{@code Result} system (out of
 * scope for this version). Built via {@code Flow.action(...)}, not constructed directly. Two
 * shapes: a plain {@link Consumer} always succeeds unless it throws (caught, logged, treated as a
 * failure — never crashes the Flow); a {@link Function} returning {@code false} is an explicit,
 * deliberate failure without needing to throw.
 */
public final class Action extends Node {

    private final Function<FlowContext, Boolean> action;

    public Action(Consumer<FlowContext> action) {
        this.action = context -> {
            action.accept(context);
            return true;
        };
    }

    public Action(Function<FlowContext, Boolean> action) {
        this.action = action;
    }

    @Override
    protected void onStart(FlowContext context) {
        boolean success;
        try {
            success = Boolean.TRUE.equals(action.apply(context));
        } catch (Exception e) {
            EngineLog.channel("Flow").error("Action threw, treating as failure: {}", e.toString());
            fail();
            return;
        }
        if (success) {
            complete();
        } else {
            fail();
        }
    }
}
