package com.dimalab.storymodengine.common.entity.search;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * {@code find_nearest_entity} — a synchronous radius scan, same as HollowEngine's own {@code
 * findEntities} (only {@code Ref.resolve()} suspends there; the search itself is synchronous too).
 * Scoped to "nearest one match" per this session's own scope decision — {@code .sme} has no
 * iteration construct today, so returning a list would have nowhere useful to go.
 */
public final class EntitySearch {

    private EntitySearch() {
    }

    /** {@code null} if nothing of {@code type} is alive within {@code radius} blocks of {@code origin}. */
    public static UUID findNearest(ServerLevel level, Vec3 origin, EntityType<?> type, double radius) {
        AABB box = AABB.ofSize(origin, radius * 2, radius * 2, radius * 2);
        double radiusSq = radius * radius;
        Entity nearest = null;
        double nearestDistSq = Double.MAX_VALUE;
        for (Entity candidate : level.getEntities(type, box, e -> e.isAlive() && e.distanceToSqr(origin) <= radiusSq)) {
            double distSq = candidate.distanceToSqr(origin);
            if (distSq < nearestDistSq) {
                nearest = candidate;
                nearestDistSq = distSq;
            }
        }
        return nearest != null ? nearest.getUUID() : null;
    }
}
