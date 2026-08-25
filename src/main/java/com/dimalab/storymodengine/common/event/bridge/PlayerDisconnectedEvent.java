package com.dimalab.storymodengine.common.event.bridge;

import com.dimalab.storymodengine.api.event.Event;
import net.minecraft.server.level.ServerPlayer;

/** Engine-level translation of Forge's {@code PlayerEvent.PlayerLoggedOutEvent} — see {@link MinecraftEventBridge}. */
public record PlayerDisconnectedEvent(ServerPlayer player) implements Event {
}
