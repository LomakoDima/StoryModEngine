package com.dimalab.storymodengine.common.flow;

import com.dimalab.storymodengine.api.flow.FlowRunState;
import com.dimalab.storymodengine.common.logging.EngineLog;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * One player's running instance of a {@link FlowDefinition} — {@link #runtime()} owns the node
 * tree exactly as before; this class adds the flow-level {@link FlowRunState} that node state alone
 * can't express (most importantly {@code PAUSED}, which has no node-level equivalent — see {@link
 * FlowRunState}'s Javadoc). {@link FlowManager} is the only code that constructs or mutates these
 * directly; a mod author only ever sees one through the narrower {@link FlowHandle}.
 */
public final class FlowInstance {

    private final ResourceLocation flowId;
    private final FlowRuntime runtime;
    private FlowRunState runState;

    public FlowInstance(ResourceLocation flowId, FlowRuntime runtime, FlowRunState runState) {
        this.flowId = flowId;
        this.runtime = runtime;
        this.runState = runState;
    }

    public ResourceLocation flowId() {
        return flowId;
    }

    public FlowRuntime runtime() {
        return runtime;
    }

    public FlowRunState runState() {
        return runState;
    }

    public void pause() {
        if (runState == FlowRunState.RUNNING) {
            runState = FlowRunState.PAUSED;
            EngineLog.channel("Flow").info("Flow {} paused for {}", flowId, playerName());
        }
    }

    public void resumeExecution() {
        if (runState == FlowRunState.PAUSED) {
            runState = FlowRunState.RUNNING;
            EngineLog.channel("Flow").info("Flow {} resumed for {}", flowId, playerName());
        }
    }

    public void cancel() {
        if (runState == FlowRunState.RUNNING || runState == FlowRunState.PAUSED) {
            runtime.cancel();
            runState = FlowRunState.CANCELLED;
            EngineLog.channel("Flow").info("Flow {} cancelled for {}", flowId, playerName());
        }
    }

    public boolean selectChoice(String transitionId) {
        return runtime.selectChoice(transitionId);
    }

    /** Reconciles {@code runState} with the root node's own terminal state after a tick — called only by {@code FlowManager}. */
    void syncFromNodeState() {
        switch (runtime.state()) {
            case COMPLETED -> runState = FlowRunState.COMPLETED;
            case FAILED -> runState = FlowRunState.FAILED;
            case CANCELLED -> runState = FlowRunState.CANCELLED;
            default -> {
                // still RUNNING at the node level — runState (RUNNING or PAUSED) is unaffected
            }
        }
    }

    public FlowState toFlowState() {
        FlowState state = runtime.toFlowState();
        state.runState = this.runState;
        return state;
    }

    private String playerName() {
        ServerPlayer player = runtime.player();
        return player == null ? "<no player>" : player.getGameProfile().getName();
    }
}
