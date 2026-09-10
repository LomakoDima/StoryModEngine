package com.dimalab.storymodengine.common.scripting.ast;

/** {@code gate (expr)} — ends the dialogue if {@code condition} fails here, compiled to {@code DialogueDefinition.Builder.gate(Evaluator<Boolean>)}. */
public record GateEntryNode(SourcePos pos, ExprNode condition) implements DialogueEntryNode {
}
