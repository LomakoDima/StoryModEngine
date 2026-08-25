package com.dimalab.storymodengine.common.dialogue.event;

import com.dimalab.storymodengine.api.event.Event;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public record DialogueLineStartedEvent(ServerPlayer player, ResourceLocation dialogueId, String nodeId, int entryIndex) implements Event {
}
