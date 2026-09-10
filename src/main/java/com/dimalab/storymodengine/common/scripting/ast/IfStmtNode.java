package com.dimalab.storymodengine.common.scripting.ast;

import java.util.List;

/** {@code if (cond) {...} elseif (cond) {...} else {...}} — {@code elseIfs}/{@code elseBlock} may be empty; the compiler desugars the whole chain into nested {@code Flow.branch} calls left-to-right. */
public record IfStmtNode(SourcePos pos, ExprNode cond, List<StmtNode> thenBlock, List<ElseIfBranch> elseIfs, List<StmtNode> elseBlock) implements StmtNode {
}
