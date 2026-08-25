package com.dimalab.storymodengine.common.flow;

import com.dimalab.storymodengine.api.flow.FlowRunState;
import net.minecraft.resources.ResourceLocation;

/**
 * A lightweight, opaque reference to one running Flow — what {@link FlowManager#start} actually
 * returns. Deliberately narrower than {@link FlowInstance}/{@link FlowRuntime}: no {@code Node}
 * tree, no {@code FlowContext}, nothing internal is reachable from here — just enough for a caller
 * (a command, in the required example) to observe and control the flow it just started.
 */
public final class FlowHandle {

    private final FlowInstance instance;

    FlowHandle(FlowInstance instance) {
        this.instance = instance;
    }

    public ResourceLocation flowId() {
        return instance.flowId();
    }

    public FlowRunState state() {
        return instance.runState();
    }

    public boolean isRunning() {
        return state() == FlowRunState.RUNNING;
    }

    public boolean isPaused() {
        return state() == FlowRunState.PAUSED;
    }

    public boolean isCompleted() {
        return state() == FlowRunState.COMPLETED;
    }

    public boolean isFailed() {
        return state() == FlowRunState.FAILED;
    }

    public boolean isCancelled() {
        return state() == FlowRunState.CANCELLED;
    }

    public void pause() {
        instance.pause();
    }

    public void resume() {
        instance.resumeExecution();
    }

    /** Routed through {@link FlowManager#cancel} — see its Javadoc for why {@link FlowInstance#cancel()} alone isn't enough. */
    public void cancel() {
        FlowManager.cancel(instance.runtime().player(), instance.flowId());
    }

    public boolean selectChoice(String transitionId) {
        return instance.selectChoice(transitionId);
    }
}
