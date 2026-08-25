package com.dimalab.storymodengine.common.flow.node;

import com.dimalab.storymodengine.api.flow.Evaluator;
import com.dimalab.storymodengine.common.flow.Flow;
import com.dimalab.storymodengine.common.flow.FlowContext;
import com.dimalab.storymodengine.common.logging.EngineLog;

import java.util.ArrayList;
import java.util.List;

/**
 * Evaluates {@code condition} exactly once, at start, and runs {@code whenTrue} or {@code
 * whenFalse} accordingly — the chosen path's own completion/failure becomes this node's. Unlike
 * {@code Choice}, the decision is made by {@link FlowContext} state, not external input.
 */
public final class Branch extends Node {

    private final Evaluator<Boolean> condition;
    private final Flow whenTrue;
    private final Flow whenFalse;

    private boolean chosenTrue;
    private Node chosen;

    public Branch(Evaluator<Boolean> condition, Flow whenTrue, Flow whenFalse) {
        this.condition = condition;
        this.whenTrue = whenTrue;
        this.whenFalse = whenFalse;
    }

    @Override
    protected void onStart(FlowContext context) {
        boolean result;
        try {
            result = Boolean.TRUE.equals(condition.evaluate(context));
        } catch (Exception e) {
            EngineLog.channel("Flow").error("Branch condition threw, treating as failure: {}", e.toString());
            fail();
            return;
        }
        chosenTrue = result;
        chosen = (result ? whenTrue : whenFalse).instantiate();
        chosen.start(context);
        EngineLog.channel("Flow").debug("Branch selected {}", result ? "TRUE" : "FALSE");
    }

    @Override
    protected void onTick(FlowContext context) {
        chosen.tick(context);
        if (chosen.state() == NodeState.COMPLETED) {
            complete();
        } else if (chosen.state() == NodeState.FAILED) {
            fail();
        }
    }

    @Override
    protected void onCancel(FlowContext context) {
        if (chosen != null) {
            chosen.cancel(context);
        }
    }

    @Override
    public Node activeLeaf() {
        return state() == NodeState.RUNNING && chosen != null ? chosen.activeLeaf() : this;
    }

    @Override
    public void collect(List<Integer> path, List<NodeSnapshot> out) {
        if (state() == NodeState.NOT_STARTED) {
            return;
        }
        out.add(new NodeSnapshot(new ArrayList<>(path), state()));
        if (state() == NodeState.RUNNING && chosen != null) {
            List<Integer> childPath = new ArrayList<>(path);
            childPath.add(chosenTrue ? 0 : 1);
            chosen.collect(childPath, out);
        }
    }

    @Override
    public void restore(List<Integer> path, List<NodeSnapshot> all, FlowContext context) {
        NodeSnapshot mine = findEntry(path, all);
        if (mine == null) {
            return;
        }
        if (mine.state != NodeState.RUNNING) {
            forceState(mine.state);
            return;
        }
        Integer childIndex = findChildIndex(path, all);
        if (childIndex == null) {
            start(context);
            return;
        }
        chosenTrue = childIndex == 0;
        markRunning();
        chosen = (chosenTrue ? whenTrue : whenFalse).instantiate();
        List<Integer> childPath = new ArrayList<>(path);
        childPath.add(childIndex);
        chosen.restore(childPath, all, context);
        EngineLog.channel("Flow").debug("Branch: restored to {}", chosenTrue ? "TRUE" : "FALSE");
    }
}
