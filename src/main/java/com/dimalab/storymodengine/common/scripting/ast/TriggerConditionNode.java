package com.dimalab.storymodengine.common.scripting.ast;

/** The {@code when ...} clause of a {@link TriggerDeclNode} — mirrors {@code Trigger}'s three kinds (LOCATION/TIME/EVENT) one-for-one. */
public sealed interface TriggerConditionNode extends SmeNode
        permits WhenEntersNode, WhenTimeNode, WhenEventNode {
}
