package com.dimalab.storymodengine.common.quest.event;

import com.dimalab.storymodengine.api.event.Event;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Posted from within the quest's own compiled Flow, the instant every objective is satisfied and before rewards run — the event {@link com.dimalab.storymodengine.common.quest.objective.QuestCompletionObjective} (and any {@code Objective.quest(...)} elsewhere) waits on. */
public record QuestCompletedEvent(ServerPlayer player, ResourceLocation questId) implements Event {
}
