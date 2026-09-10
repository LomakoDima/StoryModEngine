package com.dimalab.storymodengine.common.scripting.ast;

public record BinaryExprNode(SourcePos pos, BinaryOp op, ExprNode left, ExprNode right) implements ExprNode {
}
