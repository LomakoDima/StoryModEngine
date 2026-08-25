package com.dimalab.storymodengine.common.cinematic;

import net.minecraft.resources.ResourceLocation;

/**
 * "At this tick, something should happen" — deliberately just a tick and an id, nothing else.
 * Both the client's {@code CutsceneRuntime} (which evaluates the full timeline anyway) and the
 * server's {@code CinematicManager} (which only counts ticks, never evaluating tracks) check
 * triggers against their own tick count and post a {@link CutsceneTriggerEvent} through the
 * existing {@code EventBus} — independently, in lockstep, since both sides load the identical
 * deterministic {@link CutsceneDefinition}. No network round-trip needed for this to work on both
 * sides at once.
 */
public record Trigger(int tick, ResourceLocation id) {
}
