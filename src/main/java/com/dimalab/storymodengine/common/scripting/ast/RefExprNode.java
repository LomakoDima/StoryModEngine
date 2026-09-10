package com.dimalab.storymodengine.common.scripting.ast;

/** A bare identifier used where a cross-reference (quest/dialogue/cutscene/command argument) is expected rather than a variable read — kept distinct from {@link VarRefExprNode} so validation can apply reference-resolution rules instead of variable-usage rules. */
public record RefExprNode(SourcePos pos, String id) implements ExprNode {
}
