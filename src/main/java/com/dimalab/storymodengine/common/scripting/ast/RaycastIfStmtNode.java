package com.dimalab.storymodengine.common.scripting.ast;

import java.util.List;

/**
 * {@code if raycast.entity { } else { }} and the fuller {@code raycast { distance 20 entities living } { }}
 * form — both desugar to this one node at parse time (the sugar form fills in {@code distance=20},
 * {@code livingOnly=false} and skips straight to the body), so the compiler only ever handles one shape.
 */
public record RaycastIfStmtNode(SourcePos pos, RaycastTarget target, int distance, boolean livingOnly, List<StmtNode> thenBlock, List<StmtNode> elseBlock) implements StmtNode {
}
