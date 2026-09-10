package com.dimalab.storymodengine.common.scripting.ast;

/** {@code EQ}/{@code NEQ}/{@code GT}/{@code LT}/{@code GTE}/{@code LTE} compare two values; {@code AND}/{@code OR} compose two booleans — {@code ExprCompiler} uses two different compiled shapes for the two groups (value comparator vs. boolean short-circuit). */
public enum BinaryOp {
    EQ, NEQ, GT, LT, GTE, LTE,
    AND, OR
}
