package com.dimalab.storymodengine.common.entity.npc;

import com.dimalab.storymodengine.api.capabilities.LevelCapability;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Which live {@link NpcEntity} (by {@link NpcEntity#scriptName()}) is which, in this level — the one
 * piece of state {@code npc_*} script commands need that nothing else already provides: an NPC's
 * script name is a plain display string (see {@link NpcDefinition}), not something vanilla can look
 * up directly the way {@code Level.getEntity(UUID)} looks up by UUID. Server-only bookkeeping, never
 * shown to a client — see {@link NpcLookup} for the read/write API built on top of this.
 */
public final class NpcNameIndex implements LevelCapability {

    public Map<String, UUID> byName = new HashMap<>();
}
