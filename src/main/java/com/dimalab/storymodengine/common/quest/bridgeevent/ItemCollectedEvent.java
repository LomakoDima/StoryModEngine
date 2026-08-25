package com.dimalab.storymodengine.common.quest.bridgeevent;

import com.dimalab.storymodengine.api.event.Event;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Engine-level translation of Forge's {@code EntityItemPickupEvent} — items picked up off the ground only; crafting/trading are not bridged in this pass (documented scope limit, see the design doc §5). */
public record ItemCollectedEvent(ServerPlayer player, ResourceLocation itemId, int count) implements Event {
}
