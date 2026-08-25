package com.dimalab.storymodengine.common.raycast;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * What {@link RaycastQuery#cast()} found — the one thing a caller needs to look at instead of
 * switching on vanilla's {@code BlockHitResult}/{@code EntityHitResult}/{@code HitResult.Type} by
 * hand. Every accessor is safe to call regardless of {@link #type()} — the {@code Optional}s are
 * simply empty when they don't apply, and {@link #position()}/{@link #distance()} are always
 * defined (on a miss, {@code position()} is the ray's own end point, exactly like vanilla's own
 * {@code HitResult.Type.MISS} still carries a location).
 *
 * <p>{@link #raw()} is the escape hatch back to the underlying vanilla {@link HitResult} for the
 * rare case something genuinely vanilla-specific is needed (e.g. {@code BlockHitResult#getDirection()}
 * for the face that was hit) — this class deliberately doesn't try to wrap every last vanilla detail.
 */
public final class RaycastResult {

    public enum HitType { BLOCK, ENTITY, MISS }

    private final HitType type;
    private final Vec3 position;
    private final double distance;
    private final BlockPos blockPos;
    private final BlockState blockState;
    private final Entity entity;
    private final HitResult raw;

    private RaycastResult(HitType type, Vec3 position, double distance, BlockPos blockPos, BlockState blockState, Entity entity, HitResult raw) {
        this.type = type;
        this.position = position;
        this.distance = distance;
        this.blockPos = blockPos;
        this.blockState = blockState;
        this.entity = entity;
        this.raw = raw;
    }

    static RaycastResult block(BlockHitResult hit, Level level, Vec3 start) {
        BlockPos pos = hit.getBlockPos();
        return new RaycastResult(HitType.BLOCK, hit.getLocation(), start.distanceTo(hit.getLocation()), pos, level.getBlockState(pos), null, hit);
    }

    static RaycastResult entity(EntityHitResult hit, Vec3 start) {
        return new RaycastResult(HitType.ENTITY, hit.getLocation(), start.distanceTo(hit.getLocation()), null, null, hit.getEntity(), hit);
    }

    static RaycastResult miss(Vec3 endPoint, Vec3 start, HitResult raw) {
        return new RaycastResult(HitType.MISS, endPoint, start.distanceTo(endPoint), null, null, null, raw);
    }

    public HitType type() {
        return type;
    }

    public boolean isHit() {
        return type != HitType.MISS;
    }

    public boolean missed() {
        return type == HitType.MISS;
    }

    public boolean hitBlock() {
        return type == HitType.BLOCK;
    }

    public boolean hitEntity() {
        return type == HitType.ENTITY;
    }

    public Optional<BlockPos> block() {
        return Optional.ofNullable(blockPos);
    }

    public Optional<BlockState> blockState() {
        return Optional.ofNullable(blockState);
    }

    public Optional<Entity> entity() {
        return Optional.ofNullable(entity);
    }

    public Vec3 position() {
        return position;
    }

    public double distance() {
        return distance;
    }

    /** Escape hatch to the raw vanilla result this was built from — never {@code null}. */
    public HitResult raw() {
        return raw;
    }

    @Override
    public String toString() {
        return switch (type) {
            case BLOCK -> "RaycastResult{BLOCK " + blockPos + " " + blockState + " @ " + distance + "}";
            case ENTITY -> "RaycastResult{ENTITY " + entity + " @ " + distance + "}";
            case MISS -> "RaycastResult{MISS @ " + position + "}";
        };
    }
}
