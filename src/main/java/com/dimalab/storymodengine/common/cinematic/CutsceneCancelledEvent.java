package com.dimalab.storymodengine.common.cinematic;

import com.dimalab.storymodengine.api.event.Event;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Posted server-side when a cutscene is stopped before it would have completed on its own (explicit stop, or replaced by a new one — see {@code CinematicManager}'s "one per player" policy). */
public record CutsceneCancelledEvent(ServerPlayer player, ResourceLocation cutsceneId) implements Event {
}
