package com.dimalab.storymodengine.common.quest.bridgeevent;

import com.dimalab.storymodengine.api.event.Event;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Engine-level translation of Forge's {@code PlayerInteractEvent.RightClickBlock} — see {@code event.bridge.MinecraftEventBridge}. */
public record BlockInteractionEvent(ServerPlayer player, BlockPos pos, ResourceLocation blockId) implements Event {
}
