package com.dimalab.storymodengine.common.scripting.ast;

/** {@code when time <dayTime>}. */
public record WhenTimeNode(SourcePos pos, int dayTime) implements TriggerConditionNode {
}
