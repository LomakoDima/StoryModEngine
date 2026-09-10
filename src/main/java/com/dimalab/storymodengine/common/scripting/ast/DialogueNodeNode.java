package com.dimalab.storymodengine.common.scripting.ast;

import java.util.List;

/** {@code node <id> { ... }} — the first one in a {@link DialogueDeclNode#nodes()} becomes the dialogue's start node, matching {@code DialogueDefinition.Builder}'s own "first .node() call is the start node" rule. */
public record DialogueNodeNode(SourcePos pos, String nodeId, List<DialogueEntryNode> entries) implements SmeNode {
}
