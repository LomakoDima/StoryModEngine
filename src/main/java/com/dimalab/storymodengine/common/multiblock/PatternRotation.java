package com.dimalab.storymodengine.common.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.Rotation;

/**
 * One of the four horizontal orientations a {@link Multiblock} pattern can be checked in — the
 * single mechanism every pattern-local coordinate is turned into a world {@link BlockPos}
 * through, never a per-direction branch. {@link #NORTH} is the identity: a pattern is authored
 * exactly as it will appear in the world under {@code NORTH} (pattern-local +X → world +X/EAST,
 * pattern-local +Z → world +Z/SOUTH — see {@code Multiblock}'s Javadoc for the full coordinate
 * convention). {@link #EAST}/{@link #SOUTH}/{@link #WEST} are that same layout turned 90°/180°/
 * 270° clockwise. Y is never rotated — only horizontal orientation varies.
 *
 * <p>Built on top of vanilla {@link Rotation}/{@link Direction} rather than any hand-rolled
 * trigonometry: {@link Rotation#rotate(Direction)} turns the pattern's local +X/+Z basis
 * directions into their rotated world equivalents once, at enum-init time, and {@link
 * #toWorldPos} is nothing but multiplying and summing those two fixed vectors — the same
 * formula for all four values and every cell in a pattern, with no per-cell allocation.
 */
public enum PatternRotation {

    NORTH(Rotation.NONE),
    EAST(Rotation.CLOCKWISE_90),
    SOUTH(Rotation.CLOCKWISE_180),
    WEST(Rotation.COUNTERCLOCKWISE_90);

    private final Vec3i xAxis;
    private final Vec3i zAxis;

    PatternRotation(Rotation vanillaRotation) {
        this.xAxis = vanillaRotation.rotate(Direction.EAST).getNormal();
        this.zAxis = vanillaRotation.rotate(Direction.SOUTH).getNormal();
    }

    /** Turns a pattern-local offset {@code (dx, dy, dz)} from {@code origin} into a world {@link BlockPos} under this rotation. */
    public BlockPos toWorldPos(BlockPos origin, int dx, int dy, int dz) {
        int worldDx = xAxis.getX() * dx + zAxis.getX() * dz;
        int worldDz = xAxis.getZ() * dx + zAxis.getZ() * dz;
        return origin.offset(worldDx, dy, worldDz);
    }
}
