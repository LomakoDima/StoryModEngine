package com.dimalab.storymodengine.api.flow;

/**
 * The state of a {@link FlowInstance} — deliberately independent of {@code NodeState} (the root
 * node's own lifecycle). {@code PAUSED} in particular has no {@code NodeState} equivalent: the root
 * node can genuinely still be {@code RUNNING} internally while its owning instance is paused —
 * {@link FlowManager#tick()} simply skips ticking a non-{@code RUNNING} instance, so the node tree
 * underneath is frozen without needing a node-level "paused" concept at all.
 */
public enum FlowRunState {
    NOT_STARTED,
    RUNNING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}
