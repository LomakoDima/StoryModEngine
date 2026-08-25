package com.dimalab.storymodengine.api.quest;

/** A quest's overall run state — {@code NOT_STARTED} is never stored explicitly, it's simply the absence of a {@link QuestProgress} entry for that quest id (see {@link QuestProgress}'s Javadoc). */
public enum QuestState {
    ACTIVE,
    COMPLETED,
    FAILED
}
