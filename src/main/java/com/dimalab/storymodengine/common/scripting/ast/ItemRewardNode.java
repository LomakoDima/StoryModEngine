package com.dimalab.storymodengine.common.scripting.ast;

/** {@code item <itemId> <count>}. */
public record ItemRewardNode(SourcePos pos, String itemId, int count) implements RewardNode {
}
