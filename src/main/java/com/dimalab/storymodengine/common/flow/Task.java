package com.dimalab.storymodengine.common.flow;

/**
 * A unit of work that may span several ticks without blocking — the foundation future systems
 * (animation, movement, scripted interactions, cutscenes — none built here) can drive through the
 * same node lifecycle everything else already uses. Not implemented for any real workload in this
 * version; only the primitive and its bridge into the node tree ({@code node.TaskAction}, built via
 * {@code Flow.task(...)}) exist yet.
 */
public interface Task {

    /** Called once per engine tick while the owning node is {@code RUNNING}. */
    Result tick(FlowContext context);

    /** Called if the owning node is cancelled mid-flight — release whatever this task holds. Default: nothing to release. */
    default void cancel(FlowContext context) {
    }
}
