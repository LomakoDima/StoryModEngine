package com.dimalab.storymodengine.client.model.animator.expr;

/** One {@code query.*} entry: reads a single float out of an {@link AnimEvalContext}. */
@FunctionalInterface
public interface QueryAccessor {
    float apply(AnimEvalContext context);
}
