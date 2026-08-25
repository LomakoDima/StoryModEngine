package com.dimalab.storymodengine.common.flow.node;

import java.util.ArrayList;
import java.util.List;

/**
 * One fact for persistence: "the node at this index-path has this state." {@link
 * com.dimalab.storymodengine.common.flow.FlowState#snapshots} is a flat list of these rather than a
 * nested tree — deliberately, so it resolves through the existing {@code SerializerRegistry}/
 * {@code PojoSerializer} without any change there: a snapshot holding a {@code List<NodeSnapshot>}
 * of itself would be a genuinely self-referential type, which {@code PojoSerializer} cannot resolve
 * (its cache can only return a serializer for a type once that type has finished building its own —
 * a truly recursive type can never finish). A flat list carries exactly the same information a
 * nested tree would, just addressed by explicit path instead of by nesting.
 *
 * <p>Only nodes that ever left {@code NOT_STARTED} get an entry — see {@link Node#collect}/{@link
 * Node#restore} for how these are produced and consumed. A single active chain ({@code Sequence}/
 * {@code Branch}/{@code Choice}) produces exactly one meaningful entry per level; {@code Parallel}
 * is the reason this exists at all — it produces one entry per child, which a flat path-based model
 * couldn't express.
 */
public final class NodeSnapshot {

    public List<Integer> path = new ArrayList<>();
    public NodeState state = NodeState.NOT_STARTED;

    public NodeSnapshot() {
    }

    public NodeSnapshot(List<Integer> path, NodeState state) {
        this.path = path;
        this.state = state;
    }
}
