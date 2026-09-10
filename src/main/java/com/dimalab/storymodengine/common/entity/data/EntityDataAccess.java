package com.dimalab.storymodengine.common.entity.data;

import com.dimalab.storymodengine.common.capabilities.Capabilities;
import com.dimalab.storymodengine.common.logging.EngineLog;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;

/**
 * Shared read/write plumbing behind the {@code entity_set_data_*}/{@code entity_get_data_*} script
 * commands — one {@link CompoundTag} per sync tier (see {@link EntityDataCapabilities}), chosen by a
 * plain {@code "never"|"owner"|"tracking"} mode string on write, searched across all three on read
 * (a read doesn't know which tier a key was written under, and there's no ambiguity risk since a
 * script author picks one tier per key by convention).
 */
public final class EntityDataAccess {

    private EntityDataAccess() {
    }

    /** {@code null} for an unrecognized mode — callers log and no-op rather than guessing. */
    public static CompoundTag tagFor(Entity entity, String syncMode) {
        return switch (syncMode.toLowerCase(java.util.Locale.ROOT)) {
            case "never" -> Capabilities.get(entity, EntityDataCapabilities.NEVER).tag;
            case "owner" -> Capabilities.get(entity, EntityDataCapabilities.OWNER).tag;
            case "tracking" -> Capabilities.get(entity, EntityDataCapabilities.TRACKING).tag;
            default -> null;
        };
    }

    public static void markAndSync(Entity entity, String syncMode) {
        switch (syncMode.toLowerCase(java.util.Locale.ROOT)) {
            case "never" -> Capabilities.markDirty(entity, EntityDataCapabilities.NEVER);
            case "owner" -> {
                Capabilities.markDirty(entity, EntityDataCapabilities.OWNER);
                Capabilities.sync(entity, EntityDataCapabilities.OWNER);
            }
            case "tracking" -> {
                Capabilities.markDirty(entity, EntityDataCapabilities.TRACKING);
                Capabilities.sync(entity, EntityDataCapabilities.TRACKING);
            }
            default -> EngineLog.channel("EntityData").warn("unknown sync mode '{}' (expected never/owner/tracking)", syncMode);
        }
    }

    /** Searches all three tiers for {@code key}, tracking first (the most common script use) — {@code null} if none has it. */
    public static CompoundTag tagContaining(Entity entity, String key) {
        CompoundTag tracking = Capabilities.get(entity, EntityDataCapabilities.TRACKING).tag;
        if (tracking.contains(key)) {
            return tracking;
        }
        CompoundTag owner = Capabilities.get(entity, EntityDataCapabilities.OWNER).tag;
        if (owner.contains(key)) {
            return owner;
        }
        CompoundTag never = Capabilities.get(entity, EntityDataCapabilities.NEVER).tag;
        return never.contains(key) ? never : null;
    }
}
