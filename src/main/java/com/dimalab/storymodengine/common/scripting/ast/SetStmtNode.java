package com.dimalab.storymodengine.common.scripting.ast;

/** {@code set <dotted-var-name> =/+=/- = <expr>}. {@code varName} is one literal dotted string ({@code "village.reputation"}), never nested field access — see {@code ast} package doc on variable naming. */
public record SetStmtNode(SourcePos pos, String varName, SetOp op, ExprNode value) implements StmtNode {
}
