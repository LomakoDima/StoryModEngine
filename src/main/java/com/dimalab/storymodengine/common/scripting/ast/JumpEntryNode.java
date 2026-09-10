package com.dimalab.storymodengine.common.scripting.ast;

/** {@code jump <id>} directly inside a dialogue node body — unconditional continuation at another node, compiled to {@code DialogueDefinition.Builder.jump(String)}. */
public record JumpEntryNode(SourcePos pos, String targetNodeId) implements DialogueEntryNode {
}
