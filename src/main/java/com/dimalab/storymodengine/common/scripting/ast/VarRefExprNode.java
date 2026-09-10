package com.dimalab.storymodengine.common.scripting.ast;

/** A read of a story variable — {@code dottedName} is one literal key string ({@code "village.reputation"} or {@code "player.gold"}); a leading {@code "player."} is the only structurally special prefix (see {@code StoryVariableStore}). */
public record VarRefExprNode(SourcePos pos, String dottedName) implements ExprNode {
}
