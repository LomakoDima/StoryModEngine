package com.dimalab.storymodengine.common.scripting.ast;

/** A literal {@code bool}/{@code int}/{@code double}/{@code string} value — {@code value}'s runtime class matches {@code type} ({@code Boolean}/{@code Long}/{@code Double}/{@code String}). */
public record LiteralExprNode(SourcePos pos, SmeValueType type, Object value) implements ExprNode {
}
