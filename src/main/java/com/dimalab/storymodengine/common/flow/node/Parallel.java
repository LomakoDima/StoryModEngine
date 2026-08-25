package com.dimalab.storymodengine.common.flow.node;

import com.dimalab.storymodengine.common.flow.Flow;
import com.dimalab.storymodengine.common.flow.FlowContext;
import com.dimalab.storymodengine.common.logging.EngineLog;

import java.util.ArrayList;
import java.util.List;

/**
 * Starts every child at once; completes once all children have completed, fails as soon as any one
 * child fails. The moment that happens, every other still-{@code RUNNING} child is genuinely
 * {@link #cancel(FlowContext) cancelled} — not silently abandoned — specifically so a sibling
 * holding an external resource (an {@code EventWaiter}'s EventBus subscription, a future {@code
 * Task}) always gets to release it via its own {@code onCancel}, never leaking. No {@code Race}/
 * {@code First}/{@code N-of-M} policies — just this one, deliberately simple rule. "Parallel" here
 * means concurrent logical execution within one engine tick, never OS threads — see {@code
 * FlowManager}'s threading notes.
 *
 * <p>Faithfully persists several simultaneously-active children: {@link #collect} records one
 * {@link NodeSnapshot} per child (its own terminal state, or a full recursive snapshot if still
 * running), and {@link #restore} replays that per child — an already-completed sibling is jumped
 * straight to {@code COMPLETED} via {@code forceState} without re-running its action, and a still-
 * running sibling resumes exactly where it left off, including through its own nested composites.
 */
public final class Parallel extends Node {

    private final List<Flow> childFlows;
    private List<Node> children;

    public Parallel(List<Flow> childFlows) {
        this.childFlows = childFlows;
    }

    @Override
    protected void onStart(FlowContext context) {
        if (childFlows.isEmpty()) {
            complete();
            return;
        }
        children = childFlows.stream().map(Flow::instantiate).toList();
        children.forEach(child -> child.start(context));
        EngineLog.channel("Flow").trace("Parallel: started {} node(s)", children.size());
    }

    @Override
    protected void onTick(FlowContext context) {
        for (Node child : children) {
            child.tick(context);
        }
        if (children.stream().anyMatch(child -> child.state() == NodeState.FAILED)) {
            EngineLog.channel("Flow").debug("Parallel: at least one node failed, cancelling the rest");
            for (Node child : children) {
                child.cancel(context);
            }
            fail();
            return;
        }
        if (children.stream().allMatch(child -> child.state() == NodeState.COMPLETED)) {
            complete();
        }
    }

    @Override
    protected void onCancel(FlowContext context) {
        if (children != null) {
            children.forEach(child -> child.cancel(context));
        }
    }

    @Override
    public void collect(List<Integer> path, List<NodeSnapshot> out) {
        if (state() == NodeState.NOT_STARTED) {
            return;
        }
        out.add(new NodeSnapshot(new ArrayList<>(path), state()));
        if (state() == NodeState.RUNNING && children != null) {
            for (int i = 0; i < children.size(); i++) {
                List<Integer> childPath = new ArrayList<>(path);
                childPath.add(i);
                children.get(i).collect(childPath, out);
            }
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
        children = childFlows.stream().map(Flow::instantiate).toList();
        List<Integer> restoredIndices = findChildIndices(path, all);
        for (int i = 0; i < children.size(); i++) {
            List<Integer> childPath = new ArrayList<>(path);
            childPath.add(i);
            if (restoredIndices.contains(i)) {
                children.get(i).restore(childPath, all, context);
            } else {
                // No recorded entry for this child at all — shouldn't normally happen (every child
                // is started the instant Parallel itself starts) but resuming it fresh is the safe
                // fallback rather than leaving it permanently NOT_STARTED.
                children.get(i).start(context);
            }
        }
        EngineLog.channel("Flow").debug("Parallel: restored {} node(s)", children.size());
    }
}
