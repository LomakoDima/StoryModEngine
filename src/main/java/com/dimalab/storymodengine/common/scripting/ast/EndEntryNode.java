package com.dimalab.storymodengine.common.scripting.ast;

/** {@code end} directly inside a dialogue node body — compiled to {@code DialogueDefinition.Builder.end()}. */
public record EndEntryNode(SourcePos pos) implements DialogueEntryNode {
}
