package com.dimalab.storymodengine.common.flow.node;

import com.dimalab.storymodengine.common.flow.Flow;
import com.dimalab.storymodengine.common.flow.FlowContext;
import com.dimalab.storymodengine.common.logging.EngineLog;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs another {@link Flow} as a single logical step of this one — composition, not a second
 * runtime: {@link #onStart} instantiates the inner {@code Flow} exactly the way any other node's
 * child is instantiated, and every lifecycle method (tick/cancel/collect/restore) delegates to that
 * inner root, the same delegation pattern {@link Branch}/{@link Choice} already use for their
 * chosen child. Built via {@code Flow.subFlow(Flow)}.
 */
public final class SubFlow extends Node {

    private final Flow inner;
    private Node innerRoot;

    public SubFlow(Flow inner) {
        this.inner = inner;
    }

    @Override
    protected void onStart(FlowContext context) {
        innerRoot = inner.instantiate();
        innerRoot.start(context);
        EngineLog.channel("Flow").trace("SubFlow: started");
    }

    @Override
    protected void onTick(FlowContext context) {
        innerRoot.tick(context);
        if (innerRoot.state() == NodeState.COMPLETED) {
            complete();
        } else if (innerRoot.state() == NodeState.FAILED) {
            fail();
        }
    }

    @Override
    protected void onCancel(FlowContext context) {
        if (innerRoot != null) {
            innerRoot.cancel(context);
        }
    }

    @Override
    public Node activeLeaf() {
        return innerRoot != null ? innerRoot.activeLeaf() : this;
    }

    @Override
    public void collect(List<Integer> path, List<NodeSnapshot> out) {
        if (state() == NodeState.NOT_STARTED) {
            return;
        }
        out.add(new NodeSnapshot(new ArrayList<>(path), state()));
        if (state() == NodeState.RUNNING && innerRoot != null) {
            List<Integer> childPath = new ArrayList<>(path);
            childPath.add(0);
            innerRoot.collect(childPath, out);
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
        markRunning();
        innerRoot = inner.instantiate();
        List<Integer> childPath = new ArrayList<>(path);
        childPath.add(0);
        innerRoot.restore(childPath, all, context);
        EngineLog.channel("Flow").debug("SubFlow: restored");
    }
}
