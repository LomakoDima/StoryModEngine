package com.dimalab.storymodengine.common.flow;

/**
 * How a wrapped {@link Flow}'s failure should be reinterpreted — see {@code Flow#withPolicy}/
 * {@code Flow#retry}/{@code Flow#fallback} and {@code node.PolicyNode}. Deliberately a wrapper
 * around any child {@code Flow} rather than a parameter baked into {@code Sequence} itself, so
 * policy stays orthogonal to control flow: one step inside a {@code Sequence} can retry without
 * changing what "failure" means for the {@code Sequence} as a whole.
 */
public enum FailurePolicy {
    /** Default — a failure propagates as a failure. */
    FAIL,
    /** A failure is reinterpreted as success — "best effort" steps. */
    IGNORE,
    /** A failure re-instantiates and re-runs the child, up to a configured attempt count, before propagating failure. */
    RETRY,
    /** A failure runs an alternative {@link Flow} instead. */
    FALLBACK
}
