package com.dimalab.storymodengine.common.flow.node;

import com.dimalab.storymodengine.common.flow.Flow;
import com.dimalab.storymodengine.common.flow.FlowContext;
import com.dimalab.storymodengine.common.logging.EngineLog;

import java.util.ArrayList;
import java.util.List;

/**
 * The standard behavior-tree "Selector" (also called "Fallback") — the OR to {@link Sequence}'s
 * AND: tries children one at a time, in order, and stops at the first one that <em>succeeds</em>.
 * A failed child moves on to the next; only running out of children (all failed) fails the whole
 * Selector. Structurally identical to {@link Sequence} with completion/failure swapped — same
 * lazy-instantiation, same collect/restore/cancel shape — added specifically to close a gap against
 * standard behavior-tree taxonomy (Sequence/Selector/Parallel/Decorator) that neither {@code
 * Branch} (a one-time, pre-evaluated if/else — never retries a different child on failure) nor
 * {@code PolicyNode}'s {@code FALLBACK} policy (exactly one alternative, not an arbitrary ordered
 * list) actually covers.
 */
public final class Selector extends Node {

    private final List<Flow> children;
    private int index;
    private Node current;

    public Selector(List<Flow> children) {
        this.children = children;
    }

    @Override
    protected void onStart(FlowContext context) {
        if (children.isEmpty()) {
            fail();
            return;
        }
        startChild(context);
    }

    @Override
    protected void onTick(FlowContext context) {
        current.tick(context);
        switch (current.state()) {
            case COMPLETED -> {
                EngineLog.channel("Flow").trace("Selector: node {} succeeded, stopping", index);
                complete();
            }
            case FAILED -> {
                EngineLog.channel("Flow").trace("Selector: node {} failed, trying next", index);
                index++;
                if (index >= children.size()) {
                    EngineLog.channel("Flow").debug("Selector: all {} node(s) failed", children.size());
                    fail();
                } else {
                    startChild(context);
                }
            }
            default -> {
                // still RUNNING — nothing to do this tick
            }
        }
    }

    private void startChild(FlowContext context) {
        current = children.get(index).instantiate();
        current.start(context);
        EngineLog.channel("Flow").trace("Selector: node {} started", index);
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
        EngineLog.channel("Flow").debug("Selector: restored to node {}", index);
    }
}
