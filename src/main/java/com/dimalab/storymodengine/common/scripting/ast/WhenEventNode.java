package com.dimalab.storymodengine.common.scripting.ast;

/** {@code when event <x.y>} — {@code eventName} is resolved against {@code EventNameRegistry}. */
public record WhenEventNode(SourcePos pos, String eventName) implements TriggerConditionNode {
}
