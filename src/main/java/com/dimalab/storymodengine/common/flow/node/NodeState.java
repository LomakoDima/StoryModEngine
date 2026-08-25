package com.dimalab.storymodengine.common.flow.node;

/**
 * A node's lifecycle — see {@link Node}'s Javadoc for the transition rules. {@link #CANCELLED} was
 * added alongside {@link Node#cancel} specifically so a cancelled node is distinguishable from one
 * that failed on its own logic — {@code Task}/{@code EventWaiter}/{@code SubFlow} need that
 * distinction to release their resources correctly and to report the right outcome upward.
 */
public enum NodeState {
    NOT_STARTED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}
