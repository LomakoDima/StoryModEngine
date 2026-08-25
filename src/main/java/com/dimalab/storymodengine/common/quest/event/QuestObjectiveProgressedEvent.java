package com.dimalab.storymodengine.common.quest.event;

import com.dimalab.storymodengine.api.event.Event;
import com.dimalab.storymodengine.api.quest.ObjectiveState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Posted by {@code QuestProgressStore} every time an objective's progress or state changes — what {@code QuestToast}/{@code QuestTrackerOverlay} react to for immediate feedback, independent of the (throttled) capability sync in §11 of the design doc. */
public record QuestObjectiveProgressedEvent(
        ServerPlayer player,
        ResourceLocation questId,
        String objectiveId,
        int progress,
        int required,
        ObjectiveState state
) implements Event {
}
