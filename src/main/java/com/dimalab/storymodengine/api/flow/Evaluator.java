package com.dimalab.storymodengine.api.flow;

import com.dimalab.storymodengine.common.flow.FlowContext;
/**
 * The shared shape for "compute a value from {@link FlowContext}" — what {@code Condition} and
 * {@code Branch} evaluate, and what a future transition-requirement or expression could reuse
 * without inventing its own function type. Deliberately just one method; complicated evaluation
 * logic belongs inside a specific {@code Evaluator} implementation (or a node's own lambda), never
 * embedded into the node classes themselves.
 */
@FunctionalInterface
public interface Evaluator<T> {

    T evaluate(FlowContext context);
}
