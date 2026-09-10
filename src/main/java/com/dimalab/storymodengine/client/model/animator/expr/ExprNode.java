package com.dimalab.storymodengine.client.model.animator.expr;

import java.util.List;

/** The parsed form of one expression — a small, fixed set of node shapes, evaluated by {@link CompiledExpression}. */
sealed interface ExprNode {

    record NumberLiteral(float value) implements ExprNode {
    }

    /** {@code namespace.key} — e.g. {@code query.is_on_ground}, {@code variable.foo}. */
    record NamespaceAccess(String namespace, String key) implements ExprNode {
    }

    record Unary(char op, ExprNode operand) implements ExprNode {
    }

    record Binary(String op, ExprNode left, ExprNode right) implements ExprNode {
    }

    record Ternary(ExprNode condition, ExprNode ifTrue, ExprNode ifFalse) implements ExprNode {
    }

    /** {@code name(arg, arg, ...)} — a small fixed math-function set, dispatched by {@link CompiledExpression}. */
    record FunctionCall(String name, List<ExprNode> args) implements ExprNode {
    }
}
