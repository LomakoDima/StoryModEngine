package com.dimalab.storymodengine.common.quest.event;

import com.dimalab.storymodengine.api.event.Event;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Posted by {@code QuestSystem.stop} — a quest reaching this engine's own "failed" outcome, distinct from simply never having been started. */
public record QuestFailedEvent(ServerPlayer player, ResourceLocation questId) implements Event {
}
