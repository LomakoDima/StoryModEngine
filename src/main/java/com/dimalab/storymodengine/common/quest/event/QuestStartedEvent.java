package com.dimalab.storymodengine.common.quest.event;

import com.dimalab.storymodengine.api.event.Event;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Posted by {@code QuestSystem.start} the instant a quest's compiled Flow is registered and running — same timing convention {@code dialogue.event.DialogueStartedEvent} uses. */
public record QuestStartedEvent(ServerPlayer player, ResourceLocation questId) implements Event {
}
