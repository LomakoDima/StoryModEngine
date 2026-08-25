package com.dimalab.storymodengine.common.cinematic;

import com.dimalab.storymodengine.api.event.Event;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Posted server-side by {@code CinematicManager.play(...)} the moment a cutscene is accepted and the start packet is sent. */
public record CutsceneStartedEvent(ServerPlayer player, ResourceLocation cutsceneId) implements Event {
}
