package com.dimalab.storymodengine.common.quest.bridgeevent;

import com.dimalab.storymodengine.api.event.Event;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Engine-level translation of Forge's {@code PlayerInteractEvent.EntityInteract} — a plain "right-clicked this entity" signal, deliberately simpler than starting a full Dialogue (see {@code Objective.talkTo} vs. {@code Objective.dialogue}). */
public record EntityInteractionEvent(ServerPlayer player, ResourceLocation entityTypeId) implements Event {
}
