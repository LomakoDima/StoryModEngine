package com.dimalab.storymodengine.common.quest;

import com.dimalab.storymodengine.api.quest.ObjectiveState;
import com.dimalab.storymodengine.api.quest.QuestState;
import java.util.Map;

/**
 * One player's persisted snapshot of one quest's progress — immutable, replaced wholesale in {@code
 * QuestProgressData.quests} on every change (never mutated field-by-field in place), the same idiom
 * {@code cinematic.persistence.CutscenePlaybackData}/{@code PersistedCutscene} already establish for
 * a record nested inside a mutable capability field. Absence of an entry for a given quest id in
 * that map *is* {@code NOT_STARTED} — there is no explicit enum value for it (see {@link
 * QuestState}'s own Javadoc).
 */
public record QuestProgress(
        QuestState state,
        Map<String, Integer> objectiveProgress,
        Map<String, ObjectiveState> objectiveStates
) {

    public static QuestProgress started() {
        return new QuestProgress(QuestState.ACTIVE, Map.of(), Map.of());
    }
}
