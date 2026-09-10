package com.dimalab.storymodengine.common.scripting.ast;

/** {@code command "<literal minecraft command>"}. */
public record CommandRewardNode(SourcePos pos, String command) implements RewardNode {
}
