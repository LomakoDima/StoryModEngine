package com.dimalab.storymodengine.common.entity.npc;

import com.dimalab.storymodengine.common.capabilities.Capabilities;
import com.dimalab.storymodengine.common.entity.ModEntities;
import com.dimalab.storymodengine.common.logging.EngineLog;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

import java.util.UUID;

/**
 * {@code npc_*} script commands' one way to turn a script name back into a live {@link NpcEntity} —
 * built on {@link NpcNameIndex} (a per-level {@code name -> UUID} capability). A stale or missing
 * mapping (server restart before the index was persisted, or a hand-spawned NPC that never went
 * through {@link #register}) self-heals via one full-level scan filtered by {@link
 * NpcEntity#scriptName()} — the same {@code getEntities} scan {@code NpcCommand.clear} already uses,
 * just level-wide instead of player-local, and only ever run on the rare miss, not per command call.
 */
public final class NpcLookup {

    /** Practical "whole level" bounds — vanilla's own world border maximum, full build height. */
    private static final AABB WHOLE_LEVEL = new AABB(-3.0E7, -512, -3.0E7, 3.0E7, 512, 3.0E7);

    private NpcLookup() {
    }

    /** Null if no live NPC currently answers to {@code name} in this level. */
    public static NpcEntity resolve(ServerLevel level, String name) {
        NpcNameIndex index = Capabilities.get(level, ModNpcCapabilities.NAME_INDEX);
        UUID id = index == null ? null : index.byName.get(name);
        if (id != null) {
            Entity entity = level.getEntity(id);
            if (entity instanceof NpcEntity npc && npc.isAlive() && name.equals(npc.scriptName())) {
                return npc;
            }
        }
        return resolveByScan(level, name, index);
    }

    private static NpcEntity resolveByScan(ServerLevel level, String name, NpcNameIndex index) {
        for (NpcEntity npc : level.getEntities(ModEntities.NPC.get(), WHOLE_LEVEL, e -> name.equals(e.scriptName()))) {
            if (index != null) {
                index.byName.put(name, npc.getUUID());
                Capabilities.markDirty(level, ModNpcCapabilities.NAME_INDEX);
            }
            return npc;
        }
        return null;
    }

    public static void register(ServerLevel level, String name, UUID id) {
        NpcNameIndex index = Capabilities.get(level, ModNpcCapabilities.NAME_INDEX);
        if (index == null) {
            EngineLog.channel("Npc").warn("npc '{}': no NpcNameIndex capability on this level — name lookups will fall back to a full scan every time", name);
            return;
        }
        index.byName.put(name, id);
        Capabilities.markDirty(level, ModNpcCapabilities.NAME_INDEX);
    }

    public static void unregister(ServerLevel level, String name) {
        NpcNameIndex index = Capabilities.get(level, ModNpcCapabilities.NAME_INDEX);
        if (index == null) {
            return;
        }
        index.byName.remove(name);
        Capabilities.markDirty(level, ModNpcCapabilities.NAME_INDEX);
    }
}
