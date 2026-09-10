package com.dimalab.storymodengine.common.scripting.ast;

/** One {@code reward { ... }} entry — mirrors {@code api.quest.reward.Reward}'s own factory set one-for-one. */
public sealed interface RewardNode extends SmeNode
        permits ItemRewardNode, XpRewardNode, CommandRewardNode, UnlockQuestRewardNode {
}
