package com.dimalab.storymodengine.common.flow.node;

import com.dimalab.storymodengine.common.flow.FlowContext;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * The base execution unit every Flow piece is — {@link Action}/{@link Condition}/{@link Sequence}/
 * {@link Parallel}/{@link Branch}/{@link Choice} and friends all extend this. Lifecycle is exactly
 * the states in {@link NodeState}: {@code start} moves {@code NOT_STARTED → RUNNING} and calls
 * {@link #onStart}; {@code tick} calls {@link #onTick} only while {@code RUNNING}; a subclass moves
 * itself to {@code COMPLETED}/{@code FAILED} via the protected {@link #complete()}/{@link #fail()}
 * helpers, or is moved to {@code CANCELLED} externally via {@link #cancel}. Nothing here knows what
 * a Minecraft tick is — {@code tick(FlowContext)} is called by whatever the integration layer
 * decides should drive it (see {@code flow.integration.FlowTickBridge}), not baked into this class.
 *
 * <h2>Cancellation</h2>
 * {@link #cancel(FlowContext)} calls {@link #onCancel} (default no-op) and moves to {@code
 * CANCELLED} — a no-op if the node isn't currently {@code RUNNING}. A composite overrides {@link
 * #onCancel} to propagate into its active child/children; a node holding an external resource
 * ({@code EventWaiter}'s subscription, a future {@code Task}) releases it there. {@link Parallel}
 * is the reason this exists as a real, propagating operation rather than a cosmetic state: when one
 * of its children fails, the others are now genuinely cancelled (not silently abandoned), so a
 * still-subscribed {@code EventWaiter} sibling doesn't leak.
 *
 * <h2>Persistence</h2>
 * {@link #collect}/{@link #restore} exist purely for persistence — see {@code FlowState}'s Javadoc
 * for the full contract. A composite reports one {@link NodeSnapshot} per node that ever left
 * {@code NOT_STARTED} (itself, then recursively whichever children are relevant) into a flat list,
 * addressed by index-path rather than nesting — this is what lets {@link Parallel} record several
 * simultaneously-active children faithfully, which a single nested path could never express. {@link
 * #restore} walks the same flat list back into a fresh tree without re-executing anything already
 * terminal.
 */
public abstract class Node {

    private NodeState state = NodeState.NOT_STARTED;

    public final NodeState state() {
        return state;
    }

    /** Moves {@code NOT_STARTED → RUNNING} and runs {@link #onStart}. A no-op if already started. */
    public final void start(FlowContext context) {
        if (state != NodeState.NOT_STARTED) {
            return;
        }
        state = NodeState.RUNNING;
        onStart(context);
    }

    /** Runs {@link #onTick} only while {@code RUNNING} — a no-op otherwise, safe to call unconditionally every engine tick. */
    public final void tick(FlowContext context) {
        if (state != NodeState.RUNNING) {
            return;
        }
        onTick(context);
    }

    /** Cancels this node (and, via {@link #onCancel}, whatever it delegates to) if it's currently {@code RUNNING}. A no-op otherwise. */
    public final void cancel(FlowContext context) {
        if (state != NodeState.RUNNING) {
            return;
        }
        onCancel(context);
        state = NodeState.CANCELLED;
    }

    /** The deepest currently-active node — {@code this} for a leaf, or the active child's own {@link #activeLeaf()} for a composite. */
    public Node activeLeaf() {
        return this;
    }

    /**
     * Appends one {@link NodeSnapshot} per node (starting with this one) that ever left {@code
     * NOT_STARTED}, addressed by {@code path}. The base (leaf) implementation is the entire
     * contract for a leaf; a composite overrides this to also recurse into its relevant children
     * with {@code path + [childIndex]}.
     */
    public void collect(List<Integer> path, List<NodeSnapshot> out) {
        if (state != NodeState.NOT_STARTED) {
            out.add(new NodeSnapshot(new ArrayList<>(path), state));
        }
    }

    /**
     * Restores this node from a previously {@link #collect}ed flat snapshot list. The base (leaf)
     * behavior: if this path has no entry, stay {@code NOT_STARTED}; if the entry is terminal,
     * jump straight to it via {@link #forceState} (no re-execution); if it's {@code RUNNING}, call
     * {@link #start} (resuming an "awaiting" leaf like {@code Choice}/{@code EventWaiter}, or
     * re-running a leaf that was mid-flight — see {@code Action}'s and {@code Wait}'s Javadoc for
     * why that specific case is a documented, accepted limitation rather than a bug). A composite
     * overrides this to find its active child/children (via {@link #findChildIndex}/{@link
     * #findChildIndices}) and recurse instead.
     */
    public void restore(List<Integer> path, List<NodeSnapshot> all, FlowContext context) {
        NodeSnapshot mine = findEntry(path, all);
        if (mine == null) {
            return;
        }
        if (mine.state == NodeState.RUNNING) {
            start(context);
        } else {
            forceState(mine.state);
        }
    }

    protected void onStart(FlowContext context) {
    }

    protected void onTick(FlowContext context) {
    }

    protected void onCancel(FlowContext context) {
    }

    protected final void complete() {
        state = NodeState.COMPLETED;
    }

    protected final void fail() {
        state = NodeState.FAILED;
    }

    /** Restores {@code RUNNING} directly, bypassing {@link #onStart} — only for {@link #restore} overrides that must not re-run a child's own start logic. */
    protected final void markRunning() {
        state = NodeState.RUNNING;
    }

    /** Jumps straight to a terminal (or {@code NOT_STARTED}) state during {@link #restore}, without executing anything. */
    protected final void forceState(NodeState newState) {
        state = newState;
    }

    protected static NodeSnapshot findEntry(List<Integer> path, List<NodeSnapshot> all) {
        for (NodeSnapshot snapshot : all) {
            if (snapshot.path.equals(path)) {
                return snapshot;
            }
        }
        return null;
    }

    /** The single child index immediately under {@code parentPath}, or {@code null} if none — for single-active-child composites (Sequence/Branch/Choice). */
    protected static Integer findChildIndex(List<Integer> parentPath, List<NodeSnapshot> all) {
        for (NodeSnapshot snapshot : all) {
            if (snapshot.path.size() == parentPath.size() + 1 && startsWith(snapshot.path, parentPath)) {
                return snapshot.path.get(parentPath.size());
            }
        }
        return null;
    }

    /** Every distinct child index immediately under {@code parentPath} — for {@link Parallel}, which may have several active children at once. */
    protected static List<Integer> findChildIndices(List<Integer> parentPath, List<NodeSnapshot> all) {
        var indices = new TreeSet<Integer>();
        for (NodeSnapshot snapshot : all) {
            if (snapshot.path.size() >= parentPath.size() + 1 && startsWith(snapshot.path, parentPath)) {
                indices.add(snapshot.path.get(parentPath.size()));
            }
        }
        return new ArrayList<>(indices);
    }

    private static boolean startsWith(List<Integer> path, List<Integer> prefix) {
        if (path.size() < prefix.size()) {
            return false;
        }
        for (int i = 0; i < prefix.size(); i++) {
            if (!path.get(i).equals(prefix.get(i))) {
                return false;
            }
        }
        return true;
    }
}
