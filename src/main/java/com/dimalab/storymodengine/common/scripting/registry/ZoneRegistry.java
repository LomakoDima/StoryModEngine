package com.dimalab.storymodengine.common.scripting.registry;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code name -> (center, radius)} for {@code when player enters "<zoneName>"} / the {@code
 * teleport} built-in command — SME's grammar deliberately has no coordinate literal (out of scope,
 * "no general-purpose language"), so a mod author registers named zones from Java instead, the same
 * way {@code @StoryCommand} lets them extend the command vocabulary.
 */
public final class ZoneRegistry {

    private static final Map<String, Zone> ZONES = new ConcurrentHashMap<>();

    private ZoneRegistry() {
    }

    public static void register(String name, Vec3 center, double radius) {
        ZONES.put(name, new Zone(center, radius));
    }

    public static void register(String name, BlockPos center, double radius) {
        register(name, Vec3.atCenterOf(center), radius);
    }

    public static Zone get(String name) {
        return ZONES.get(name);
    }

    public record Zone(Vec3 center, double radius) {
    }
}
