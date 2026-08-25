package com.dimalab.storymodengine.common.raycast;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The entry point for the engine's raycast abstraction layer — turns vanilla's
 * {@code Level#clip(ClipContext)} / {@code ProjectileUtil.getEntityHitResult} boilerplate into
 *
 * <pre>{@code
 * var result = Raycast.from(player)
 *         .distance(20)
 *         .blocks()
 *         .cast();
 * }</pre>
 *
 * <p>This is a reusable utility layer, not a runtime: {@link #from} just builds a {@link
 * RaycastQuery}, and {@link RaycastQuery#cast()} runs one synchronous vanilla raycast and returns.
 * No tick loop, no persistent state, nothing registered with {@code EngineBootstrap} — see {@code
 * ARCHITECTURE.md}'s {@code raycast} section for the full reasoning.
 *
 * <p>Works identically server- or client-side (both {@code Level#clip} and {@code ProjectileUtil}
 * are common-side vanilla APIs) — only {@link RaycastQuery#debug} visualization has a client-only
 * code path, and that path is isolated so a dedicated server never loads it (see {@link
 * RaycastDebug}'s own doc).
 */
public final class Raycast {

    private Raycast() {
    }

    /** Casts from {@code entity}'s eye position, looking along its view vector. Excludes {@code entity} itself from entity search. */
    public static RaycastQuery from(Entity entity) {
        return new RaycastQuery(entity.level(), entity.getEyePosition(), entity)
                .direction(entity.getViewVector(1.0F));
    }

    /** Casts from a bare position in {@code level} — call {@link RaycastQuery#to} or {@link RaycastQuery#direction}+{@link RaycastQuery#distance} next. */
    public static RaycastQuery from(Level level, Vec3 start) {
        return new RaycastQuery(level, start, null);
    }

    /** Casts from a bare position with a known direction — still needs {@link RaycastQuery#distance}. */
    public static RaycastQuery from(Level level, Vec3 start, Vec3 direction) {
        return new RaycastQuery(level, start, null).direction(direction);
    }

    /** Whether solid geometry (collision shape, no fluids — the same primitive {@link RaycastQuery#collision()} defaults to) blocks the line between two points. */
    public static boolean hasLineOfSight(Level level, Vec3 from, Vec3 to) {
        return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null))
                .getType() == HitResult.Type.MISS;
    }

    /** Whether {@code from} can see {@code to} — same level required, eye position to eye position, {@code from} excluded from its own ray's collision context. */
    public static boolean hasLineOfSight(Entity from, Entity to) {
        if (from.level() != to.level()) {
            return false;
        }
        Vec3 eyeFrom = from.getEyePosition();
        Vec3 eyeTo = to.getEyePosition();
        return from.level().clip(new ClipContext(eyeFrom, eyeTo, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, from))
                .getType() == HitResult.Type.MISS;
    }
}
