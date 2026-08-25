package com.dimalab.storymodengine.common.dialogue.event;

import com.dimalab.storymodengine.api.event.Event;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Posted when the player continues past a line — after typewriter reveal is a purely client-side concern this event knows nothing about. */
public record DialogueLineCompletedEvent(ServerPlayer player, ResourceLocation dialogueId, String nodeId, int entryIndex) implements Event {
}
