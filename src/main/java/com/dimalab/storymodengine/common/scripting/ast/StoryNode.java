package com.dimalab.storymodengine.common.scripting.ast;

import java.util.List;

/** {@code story <id> { ... }} — the top-level container a {@code .sme} file is made of; {@code declarations} holds a mix of {@link VarDeclNode}/{@link DialogueDeclNode}/{@link QuestDeclNode}/{@link TriggerDeclNode}/{@link SequenceDeclNode}/{@link IncludeNode}. */
public record StoryNode(SourcePos pos, String id, List<MetadataTag> tags, List<SmeNode> declarations) implements SmeNode {
}
