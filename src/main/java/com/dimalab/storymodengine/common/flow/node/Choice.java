package com.dimalab.storymodengine.common.flow.node;

import com.dimalab.storymodengine.common.flow.FlowContext;
import com.dimalab.storymodengine.common.flow.Transition;
import com.dimalab.storymodengine.common.logging.EngineLog;

import java.util.ArrayList;
import java.util.List;

/**
 * Picks the next logical path from external input — not a dialogue system: no UI, no text, no
 * dialogue manager, just {@link #select(String, FlowContext)} moving the flow down one of {@link
 * Transition}'s named options. On {@link #onStart}, a {@code Choice} does nothing but stay {@code
 * RUNNING} — it waits indefinitely (no timeout, out of scope for this version) until something
 * external — a command, in the required example — calls {@link #select}.
 */
public final class Choice extends Node {

    private final List<Transition> options;
    private int selectedIndex = -1;
    private Node selected;

    public Choice(List<Transition> options) {
        this.options = options;
    }

    @Override
    protected void onStart(FlowContext context) {
        EngineLog.channel("Flow").info("Choice awaiting selection: {}", options.stream().map(Transition::id).toList());
    }

    @Override
    protected void onTick(FlowContext context) {
        if (selected == null) {
            return; // still awaiting selection
        }
        selected.tick(context);
        if (selected.state() == NodeState.COMPLETED) {
            complete();
        } else if (selected.state() == NodeState.FAILED) {
            fail();
        }
    }

    /** Resolves this choice to {@code transitionId}'s target, if it's still awaiting one. Returns whether the selection was accepted. */
    public boolean select(String transitionId, FlowContext context) {
        if (state() != NodeState.RUNNING || selected != null) {
            EngineLog.channel("Flow").warn("Choice: select({}) ignored — not currently awaiting a choice", transitionId);
            return false;
        }
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).id().equals(transitionId)) {
                selectedIndex = i;
                selected = options.get(i).target().instantiate();
                selected.start(context);
                EngineLog.channel("Flow").info("Choice selected: {}", transitionId);
                return true;
            }
        }
        EngineLog.channel("Flow").warn("Choice: no such option '{}' (available: {})",
                transitionId, options.stream().map(Transition::id).toList());
        return false;
    }

    @Override
    protected void onCancel(FlowContext context) {
        if (selected != null) {
            selected.cancel(context);
        }
    }

    @Override
    public Node activeLeaf() {
        return selected != null ? selected.activeLeaf() : this;
    }

    @Override
    public void collect(List<Integer> path, List<NodeSnapshot> out) {
        if (state() == NodeState.NOT_STARTED) {
            return;
        }
        out.add(new NodeSnapshot(new ArrayList<>(path), state()));
        if (state() == NodeState.RUNNING && selected != null) {
            List<Integer> childPath = new ArrayList<>(path);
            childPath.add(selectedIndex);
            selected.collect(childPath, out);
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
            start(context); // still awaiting selection — safe, nothing was running yet
            return;
        }
        selectedIndex = childIndex;
        markRunning();
        selected = options.get(selectedIndex).target().instantiate();
        List<Integer> childPath = new ArrayList<>(path);
        childPath.add(selectedIndex);
        selected.restore(childPath, all, context);
        EngineLog.channel("Flow").debug("Choice: restored to selection '{}'", options.get(selectedIndex).id());
    }
}
