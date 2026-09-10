package com.dimalab.storymodengine.common.scripting.ast;

import java.util.List;

/** A bare {@code { ... }} block directly inside a dialogue node — an unconditional side effect, compiled to {@code DialogueDefinition.Builder.action(DialogueCommand)}. */
public record ActionEntryNode(SourcePos pos, List<StmtNode> body) implements DialogueEntryNode {
}
