package com.dimalab.storymodengine.common.scripting.ast;

/** {@code unlock_quest <id>}. */
public record UnlockQuestRewardNode(SourcePos pos, String questId) implements RewardNode {
}
