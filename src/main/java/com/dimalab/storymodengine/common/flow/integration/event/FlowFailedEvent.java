package com.dimalab.storymodengine.common.flow.integration.event;

import com.dimalab.storymodengine.api.event.Event;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Posted through the existing {@code Events} bus when a Flow's root node reaches {@code FAILED}. */
public record FlowFailedEvent(ServerPlayer player, ResourceLocation flowId) implements Event {
}
