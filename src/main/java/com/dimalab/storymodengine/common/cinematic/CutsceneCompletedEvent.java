package com.dimalab.storymodengine.common.cinematic;

import com.dimalab.storymodengine.api.event.Event;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Posted server-side by {@code CinematicManager} once its own tick count reaches the cutscene's
 * duration — this is what a {@code Flow} waits for via the existing {@code Flow.waitForEvent(
 * CutsceneCompletedEvent.class, (ctx, e) -> ...)}, no dedicated Flow node needed (see
 * {@code ARCHITECTURE.md}'s Flow-integration section).
 */
public record CutsceneCompletedEvent(ServerPlayer player, ResourceLocation cutsceneId) implements Event {
}
