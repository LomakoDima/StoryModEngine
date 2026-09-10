package com.dimalab.storymodengine.common.scripting.ast;

public record UnaryExprNode(SourcePos pos, UnaryOp op, ExprNode operand) implements ExprNode {
}
