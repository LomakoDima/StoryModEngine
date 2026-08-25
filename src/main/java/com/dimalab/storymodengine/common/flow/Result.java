package com.dimalab.storymodengine.common.flow;

/**
 * The deterministic outcome of one {@link Task} tick — used narrowly (just {@link Task}, and
 * {@link Evaluator} where a caller genuinely needs "still working" as a distinct outcome from a
 * plain value). {@code Action}/{@code Condition} keep their existing boolean-based factories
 * unchanged; retrofitting {@code Result} onto them would touch already-tested, working code for no
 * concrete benefit — {@code NodeState} already gives them deterministic success/failure/running
 * propagation.
 */
public enum Result {
    SUCCESS,
    RUNNING,
    FAILURE,
    CANCELLED
}
