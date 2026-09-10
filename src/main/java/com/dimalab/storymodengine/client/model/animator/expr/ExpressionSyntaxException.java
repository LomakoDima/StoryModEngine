package com.dimalab.storymodengine.client.model.animator.expr;

/** A malformed expression source string — unchecked, since it's always caught once at compile time (see {@link AnimExpr}), never at eval time. */
public final class ExpressionSyntaxException extends RuntimeException {
    public ExpressionSyntaxException(String message) {
        super(message);
    }
}
