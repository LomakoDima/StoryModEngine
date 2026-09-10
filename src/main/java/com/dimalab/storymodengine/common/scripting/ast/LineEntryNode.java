package com.dimalab.storymodengine.common.scripting.ast;

/** {@code Speaker: "text"}. */
public record LineEntryNode(SourcePos pos, String speaker, String text) implements DialogueEntryNode {
}
