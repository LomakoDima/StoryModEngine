package com.dimalab.storymodengine.client.model.animator;

import com.dimalab.storymodengine.client.model.animator.expr.AnimationExpression;

/**
 * One candidate move from {@code from} (or {@link #ANY_STATE}) to {@code to}, taken once {@code
 * condition} evaluates true and — if set — the source state has played at least {@code exitTime}
 * seconds. Among simultaneously-eligible transitions, {@link AnimationController} picks the highest
 * {@code priority}, tie-broken by the lexicographically smaller {@code to}.
 */
public record AnimationControllerTransitionSpec(
        String from,
        String to,
        AnimationExpression condition,
        AnimationExpression duration,
        int priority,
        Float exitTime
) {
    public static final String ANY_STATE = "__any__";

    public static AnimationControllerTransitionSpec of(String from, String to) {
        return new AnimationControllerTransitionSpec(from, to, AnimationExpression.TRUE, AnimationExpression.ZERO, 0, null);
    }

    public AnimationControllerTransitionSpec withCondition(AnimationExpression condition) {
        return new AnimationControllerTransitionSpec(from, to, condition, duration, priority, exitTime);
    }

    public AnimationControllerTransitionSpec withDuration(AnimationExpression duration) {
        return new AnimationControllerTransitionSpec(from, to, condition, duration, priority, exitTime);
    }

    public AnimationControllerTransitionSpec withPriority(int priority) {
        return new AnimationControllerTransitionSpec(from, to, condition, duration, priority, exitTime);
    }

    public AnimationControllerTransitionSpec withExitTime(Float exitTime) {
        return new AnimationControllerTransitionSpec(from, to, condition, duration, priority, exitTime);
    }
}
