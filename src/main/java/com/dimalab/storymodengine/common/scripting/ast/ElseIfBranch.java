package com.dimalab.storymodengine.common.scripting.ast;

import java.util.List;

/** One {@code elseif (cond) { ... }} clause of an {@link IfStmtNode} — not itself an {@link SmeNode}, just a plain data carrier for one of the chain's links. */
public record ElseIfBranch(ExprNode cond, List<StmtNode> block) {
}
