package com.dimalab.storymodengine.common.quest.bridgeevent;

import com.dimalab.storymodengine.api.event.Event;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Engine-level translation of Forge's {@code LivingDeathEvent} when the kill credit belongs to a {@code ServerPlayer} — see {@code event.bridge.MinecraftEventBridge}. */
public record EntityKilledEvent(ServerPlayer killer, ResourceLocation entityTypeId) implements Event {
}
