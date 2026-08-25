package com.dimalab.storymodengine.api.capabilities;

/**
 * Marker a {@code @Capability} data class implements to declare it attaches to every {@code
 * net.minecraft.world.entity.Entity} (players included — there is no separate player-only owner
 * kind, see {@code ARCHITECTURE.md} for why). Exactly one of {@link EntityCapability}/{@link
 * BlockEntityCapability}/{@link LevelCapability} must be implemented; {@code
 * CapabilityDiscovery} skips (with a warning) a data class implementing none or more than one.
 */
public interface EntityCapability {
}
