package com.dimalab.storymodengine.common.scripting.ast;

import java.util.List;

/** {@code quest <id> { title ... objective ... reward { ... } }}. */
public record QuestDeclNode(SourcePos pos, String id, List<MetadataTag> tags, String title, String description, List<String> prerequisites, List<ObjectiveNode> objectives, List<RewardNode> rewards) implements SmeNode {
}
