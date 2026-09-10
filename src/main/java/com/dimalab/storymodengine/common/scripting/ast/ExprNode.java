package com.dimalab.storymodengine.common.scripting.ast;

/** One expression node — compiles to a real {@code Evaluator<T>} closure at compile time (see {@code ExprCompiler}), never interpreted node-by-node at runtime. */
public sealed interface ExprNode extends SmeNode
        permits LiteralExprNode, VarRefExprNode, UnaryExprNode, BinaryExprNode, RefExprNode {
}
