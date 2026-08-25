package com.dimalab.storymodengine.common.flow;

import com.dimalab.storymodengine.api.flow.FlowRunState;
import com.dimalab.storymodengine.common.flow.node.Choice;
import com.dimalab.storymodengine.common.flow.node.Node;
import com.dimalab.storymodengine.common.flow.node.NodeSnapshot;
import com.dimalab.storymodengine.common.flow.node.NodeState;
import com.dimalab.storymodengine.common.logging.EngineLog;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Owns exactly one instantiated {@code Node} tree, private to one player's execution of one {@link
 * Flow} — this is the "Runtime" half of "Flow Definition ≠ Flow Runtime". Two players running the
 * same {@code Flow} each get their own {@code FlowRuntime} with their own tree; nothing here is
 * shared between them beyond the immutable {@link Flow} definition and its lambdas. {@link
 * FlowInstance} is the layer above this that adds flow-level run state (pause/cancel/completion as
 * seen from outside) — this class only ever knows about the node tree itself.
 */
public final class FlowRuntime {

    private final ResourceLocation flowId;
    private final FlowContext context;
    private final Node root;

    private FlowRuntime(ResourceLocation flowId, FlowContext context, Node root) {
        this.flowId = flowId;
        this.context = context;
        this.root = root;
    }

    /** Instantiates {@code flow} fresh and starts it. */
    public static FlowRuntime start(ResourceLocation flowId, Flow flow, FlowContext context) {
        Node root = flow.instantiate();
        FlowRuntime runtime = new FlowRuntime(flowId, context, root);
        root.start(context);
        EngineLog.channel("Flow").info("Flow {} started for {}", flowId, playerName(context));
        return runtime;
    }

    /** Instantiates {@code flow} fresh and replays it to a previously saved {@link FlowState} — see {@code Node#restore}. */
    public static FlowRuntime resume(ResourceLocation flowId, Flow flow, FlowContext context, FlowState state) {
        Node root = flow.instantiate();
        FlowRuntime runtime = new FlowRuntime(flowId, context, root);
        root.restore(List.of(), state.snapshots, context);
        EngineLog.channel("Flow").info("Flow {} restored for {} (was {})", flowId, playerName(context), state.rootState);
        return runtime;
    }

    /** {@code context.player()} is only ever null in tests exercising the Flow core without a real world — never in normal play. */
    private static String playerName(FlowContext context) {
        return context.player() == null ? "<no player>" : context.player().getGameProfile().getName();
    }

    public ResourceLocation flowId() {
        return flowId;
    }

    public ServerPlayer player() {
        return context.player();
    }

    public NodeState state() {
        return root.state();
    }

    /** Advances the root by one step. A no-op once the flow has reached a terminal state. */
    public void tick() {
        root.tick(context);
    }

    /** Cancels the whole tree — see {@code Node#cancel}. A no-op if the root isn't currently {@code RUNNING}. */
    public void cancel() {
        root.cancel(context);
    }

    /** Resolves the flow's currently-awaiting {@code Choice}, if any — see {@code Choice#select}. */
    public boolean selectChoice(String transitionId) {
        Node active = root.activeLeaf();
        if (!(active instanceof Choice choice)) {
            EngineLog.channel("Flow").warn(
                    "Flow {}: selectChoice({}) ignored — not currently awaiting a choice", flowId, transitionId);
            return false;
        }
        return choice.select(transitionId, context);
    }

    /** The current snapshot, ready for persistence — {@code runState} is left at its default; {@code FlowInstance} fills that in. */
    public FlowState toFlowState() {
        List<NodeSnapshot> snapshots = new ArrayList<>();
        root.collect(List.of(), snapshots);
        return new FlowState(flowId, FlowRunState.NOT_STARTED, root.state(), snapshots);
    }
}
