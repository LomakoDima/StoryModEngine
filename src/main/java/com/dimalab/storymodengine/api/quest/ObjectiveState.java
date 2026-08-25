package com.dimalab.storymodengine.api.quest;

/** One objective's own state within an active quest — independent of the quest's overall {@link QuestState}, since several objectives can be at different points at once (only relevant for a quest whose objectives run in {@code Flow.parallel}, but tracked uniformly regardless). */
public enum ObjectiveState {
    INACTIVE,
    ACTIVE,
    COMPLETED,
    FAILED
}
