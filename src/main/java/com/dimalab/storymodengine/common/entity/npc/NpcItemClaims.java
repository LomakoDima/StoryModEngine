package com.dimalab.storymodengine.common.entity.npc;

import com.dimalab.storymodengine.api.capabilities.LevelCapability;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Which dropped item entities are already spoken for by an {@code npc_collect_items} goal, so two NPCs
 * never converge on the same stack — same shape as {@link NpcNameIndex}, keyed by item entity UUID
 * instead of script name. See {@link ItemClaims} for the actual claim/release logic.
 */
public final class NpcItemClaims implements LevelCapability {

    public Map<UUID, UUID> claimedBy = new HashMap<>();
}
