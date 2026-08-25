package com.dimalab.storymodengine.common.dialogue.event;

import com.dimalab.storymodengine.api.event.Event;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public record DialogueCompletedEvent(ServerPlayer player, ResourceLocation dialogueId) implements Event {
}
