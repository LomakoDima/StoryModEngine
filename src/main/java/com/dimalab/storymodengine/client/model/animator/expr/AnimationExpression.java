package com.dimalab.storymodengine.client.model.animator.expr;

/**
 * A layer weight, clip speed, transition condition/duration, or procedural bone component, stored as
 * source text rather than a pre-parsed form — parsing is deferred to {@link AnimExpr}, which compiles
 * and caches each distinct source string once, the first time it's actually evaluated.
 *
 * <p>Modeled on HollowEngine's own {@code AnimationExpression} wrapper: a plain string carrier, not
 * an expression tree itself, so that two specs built independently from the same text are equal
 * (records compare by value) without needing the parser to have run yet.
 */
public record AnimationExpression(String source) {

    public static final AnimationExpression ZERO = new AnimationExpression("0");
    public static final AnimationExpression ONE = new AnimationExpression("1");
    public static final AnimationExpression TRUE = new AnimationExpression("true");
    public static final AnimationExpression FALSE = new AnimationExpression("false");

    public static AnimationExpression of(String source) {
        return new AnimationExpression(source);
    }
}
