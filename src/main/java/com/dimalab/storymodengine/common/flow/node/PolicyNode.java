package com.dimalab.storymodengine.common.flow.node;

import com.dimalab.storymodengine.common.flow.FailurePolicy;
import com.dimalab.storymodengine.common.flow.Flow;
import com.dimalab.storymodengine.common.flow.FlowContext;
import com.dimalab.storymodengine.common.logging.EngineLog;

import java.util.ArrayList;
import java.util.List;

/**
 * Reinterprets a wrapped {@link Flow}'s failure according to a {@link FailurePolicy} — a small
 * wrapper any {@code Flow} can be composed with, rather than a parameter baked into {@code
 * Sequence}, so policy stays orthogonal to control flow. Built via {@code Flow.withPolicy}/{@code
 * Flow.retry}/{@code Flow.fallback}.
 *
 * <p><b>Persistence</b>: like {@code Sequence}/{@code Selector}, restore resumes exactly where the
 * node was — a {@code RETRY} in progress continues at the same attempt number, and a policy running
 * its {@code FALLBACK} branch resumes the fallback rather than restarting the primary. This needs
 * one extra bit of state beyond "which child" ({@code Sequence}'s single active-index model isn't
 * enough — {@code attempt} and "primary vs. fallback" both have to survive too), so the child index
 * written into the path doubles as that encoding rather than indexing into an actual list of
 * children (this node only ever has two: {@link #primary}/{@link #fallback}): a non-negative index
 * {@code n} means "running {@link #primary}, this is attempt {@code n + 1}"; {@code -1} means
 * "running {@link #fallback}". Nothing outside this class ever reads that index as a list position,
 * so the encoding is a private implementation detail.
 */
public final class PolicyNode extends Node {

    private final FailurePolicy policy;
    private final Flow primary;
    private final Flow fallback;
    private final int maxAttempts;

    private int attempt;
    private boolean runningFallback;
    private Node current;

    public PolicyNode(FailurePolicy policy, Flow primary, Flow fallback, int maxAttempts) {
        this.policy = policy;
        this.primary = primary;
        this.fallback = fallback;
        this.maxAttempts = maxAttempts;
    }

    @Override
    protected void onStart(FlowContext context) {
        attempt = 1;
        startChild(primary, context);
    }

    @Override
    protected void onTick(FlowContext context) {
        current.tick(context);
        switch (current.state()) {
            case COMPLETED -> complete();
            case FAILED -> handleFailure(context);
            default -> {
                // still RUNNING — nothing to do this tick
            }
        }
    }

    private void handleFailure(FlowContext context) {
        switch (policy) {
            case FAIL -> fail();
            case IGNORE -> {
                EngineLog.channel("Flow").debug("PolicyNode: IGNORE — treating failure as success");
                complete();
            }
            case RETRY -> {
                if (attempt < maxAttempts) {
                    attempt++;
                    EngineLog.channel("Flow").debug("PolicyNode: RETRY attempt {}/{}", attempt, maxAttempts);
                    startChild(primary, context);
                } else {
                    EngineLog.channel("Flow").debug("PolicyNode: RETRY exhausted after {} attempt(s)", maxAttempts);
                    fail();
                }
            }
            case FALLBACK -> {
                if (!runningFallback && fallback != null) {
                    runningFallback = true;
                    EngineLog.channel("Flow").debug("PolicyNode: FALLBACK — running alternative");
                    startChild(fallback, context);
                } else {
                    fail();
                }
            }
        }
    }

    private void startChild(Flow flow, FlowContext context) {
        current = flow.instantiate();
        current.start(context);
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
            int index = runningFallback ? -1 : (attempt - 1);
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
        markRunning();
        if (childIndex < 0) {
            runningFallback = true;
            attempt = 1;
            current = fallback.instantiate();
        } else {
            runningFallback = false;
            attempt = childIndex + 1;
            current = primary.instantiate();
        }
        List<Integer> childPath = new ArrayList<>(path);
        childPath.add(childIndex);
        current.restore(childPath, all, context);
        EngineLog.channel("Flow").debug("PolicyNode: restored (fallback={}, attempt={})", runningFallback, attempt);
    }
}
