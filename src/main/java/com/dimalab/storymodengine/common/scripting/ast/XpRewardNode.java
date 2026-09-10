package com.dimalab.storymodengine.common.scripting.ast;

/** {@code xp <amount>}. */
public record XpRewardNode(SourcePos pos, int amount) implements RewardNode {
}
