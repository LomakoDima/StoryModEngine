package com.dimalab.storymodengine.api.capabilities;

/**
 * Marker a {@code @Capability} data class implements to declare it attaches to every {@code
 * net.minecraft.world.level.Level} (client and server alike — persistence only actually happens
 * server-side, via Forge's own {@code LevelCapabilityData}/{@code SavedData}). See {@link
 * EntityCapability} — same mechanism, different owner kind.
 */
public interface LevelCapability {
}
