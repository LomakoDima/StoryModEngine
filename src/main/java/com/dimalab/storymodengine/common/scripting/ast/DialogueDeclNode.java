package com.dimalab.storymodengine.common.scripting.ast;

import java.util.List;

/** {@code dialogue <id> { ... }}. Bare entries directly under the dialogue id (no explicit {@code node} blocks) are sugar for one implicit "start" node — the parser normalizes both forms into this same {@code nodes} list. */
public record DialogueDeclNode(SourcePos pos, String id, List<MetadataTag> tags, List<DialogueNodeNode> nodes) implements SmeNode {
}
