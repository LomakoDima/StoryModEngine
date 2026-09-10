package com.dimalab.storymodengine.common.entity;

import com.dimalab.storymodengine.common.entity.npc.NpcEntity;
import com.dimalab.storymodengine.common.entity.npc.NpcLookup;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.UUID;

/**
 * Resolves a script-facing "target" string to a live {@link Entity} two ways: an NPC's own
 * script-assigned name (via the existing {@link NpcLookup}), or a raw UUID string — the shape
 * {@code find_nearest_entity}/{@code entity_get_or_set_data_string} bind their result as (see
 * {@code EntitySearchCommands}), since a UUID is the only thing this engine's closed {@code
 * SmeValueType} set can carry as a plain {@code STRING} script variable. Tried in that order because
 * an NPC name is the far more common target and a stray UUID-shaped script name would be unusual.
 */
public final class EntityTargeting {

    private EntityTargeting() {
    }

    /** {@code null} if {@code target} matches neither a live NPC name nor a loaded entity's UUID. */
    public static Entity resolve(ServerLevel level, String target) {
        if (target == null || target.isEmpty()) {
            return null;
        }
        NpcEntity npc = NpcLookup.resolve(level, target);
        if (npc != null) {
            return npc;
        }
        try {
            UUID id = UUID.fromString(target);
            return level.getEntity(id);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
