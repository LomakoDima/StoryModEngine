package com.dimalab.storymodengine.common.dialogue.event;

import com.dimalab.storymodengine.api.event.Event;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/** {@code choiceIds} lists only the choices that passed their condition — the same filtered list sent to the client. */
public record DialogueChoiceOpenedEvent(ServerPlayer player, ResourceLocation dialogueId, String nodeId, int entryIndex, List<String> choiceIds) implements Event {
}
