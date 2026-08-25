package com.dimalab.storymodengine.common.flow.node;

import com.dimalab.storymodengine.common.flow.Flow;
import com.dimalab.storymodengine.common.flow.FlowContext;
import com.dimalab.storymodengine.common.logging.EngineLog;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs its children one at a time, in order. A completed child advances to the next; a failed
 * child fails the whole sequence immediately — later children are never started. Children are
 * instantiated lazily, one at a time, right before each starts (not all upfront), so a sequence
 * that fails early never even builds the nodes it never reached.
 */
public final class Sequence extends Node {

    private final List<Flow> children;
    private int index;
    private Node current;

    public Sequence(List<Flow> children) {
        this.children = children;
    }

    @Override
    protected void onStart(FlowContext context) {
        if (children.isEmpty()) {
            complete();
            return;
        }
        startChild(context);
    }

    @Override
    protected void onTick(FlowContext context) {
        current.tick(context);
        switch (current.state()) {
            case COMPLETED -> {
                EngineLog.channel("Flow").trace("Sequence: node {} completed", index);
                index++;
                if (index >= children.size()) {
                    complete();
                } else {
                    startChild(context);
                }
            }
            case FAILED -> {
                EngineLog.channel("Flow").debug("Sequence: node {} failed, aborting remaining {} node(s)",
                        index, children.size() - index - 1);
                fail();
            }
            default -> {
                // still RUNNING — nothing to do this tick
            }
        }
    }

    private void startChild(FlowContext context) {
        current = children.get(index).instantiate();
        current.start(context);
        EngineLog.channel("Flow").trace("Sequence: node {} started", index);
    }

    @Override
    protected void onCancel(FlowContext context) {
        if (current != null) {
            current.cancel(context);
        }
    }

    @Override
    public Node activeLeaf() {
        return state() == NodeState.RUNNING && current != null ? current.activeLeaf() : this;
    }

    @Override
    public void collect(List<Integer> path, List<NodeSnapshot> out) {
        if (state() == NodeState.NOT_STARTED) {
            return;
        }
        out.add(new NodeSnapshot(new ArrayList<>(path), state()));
        if (state() == NodeState.RUNNING && current != null) {
            List<Integer> childPath = new ArrayList<>(path);
            childPath.add(index);
            current.collect(childPath, out);
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
        index = childIndex;
        markRunning();
        current = children.get(index).instantiate();
        List<Integer> childPath = new ArrayList<>(path);
        childPath.add(index);
        current.restore(childPath, all, context);
        EngineLog.channel("Flow").debug("Sequence: restored to node {}", index);
    }
}
