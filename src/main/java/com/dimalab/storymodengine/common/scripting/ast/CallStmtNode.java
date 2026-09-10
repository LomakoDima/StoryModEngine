package com.dimalab.storymodengine.common.scripting.ast;

import java.util.List;

/** {@code call <sequenceId> [args]} — invokes a named {@code sequence} declaration as a sub-flow; compiles via {@code Flow.subFlow(Flow.lazy(...))} so forward references within the same file resolve. */
public record CallStmtNode(SourcePos pos, String sequenceId, List<ExprNode> args) implements StmtNode {
}
