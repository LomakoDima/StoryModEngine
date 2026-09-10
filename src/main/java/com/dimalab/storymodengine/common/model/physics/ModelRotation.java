package com.dimalab.storymodengine.common.model.physics;

import net.minecraft.core.Direction;

/**
 * Keeps a model's <b>rendered</b> orientation and its <b>collision</b> orientation in agreement.
 *
 * <p>{@code voxel.ShapeRotation} turns a collision shape in clockwise quarter-steps from {@link
 * Direction#NORTH} — {@code ShapeBox.rotatedQuarterTurns} maps {@code (x, z) -> (1 - z, x)}, which
 * takes the north face to the east face. The matching render rotation is therefore −90° per step.
 *
 * <p>It is emphatically <b>not</b> {@link Direction#toYRot()}, and using that was a real bug: {@code
 * toYRot()} gives 180° for {@code NORTH} where the shape doesn't turn at all, and +270° for {@code
 * EAST} where the shape turns −90°. A block was drawn facing one way while you collided with it
 * facing another — invisible until hitbox display was switched on.
 *
 * <p>Deliberately a plain geometry helper with no Minecraft {@code Block} in sight: it's used by the
 * block, by its renderer, and by the self-test, and the last of those must be able to run without
 * bootstrapping Minecraft's registries.
 */
public final class ModelRotation {

    private ModelRotation() {
    }

    /** Degrees to rotate a render about Y so it lines up with {@code ShapeDefinition.rotated(facing)}. */
    public static float renderYawFor(Direction facing) {
        return -90f * quarterTurnsFromNorth(facing);
    }

    /** Mirrors {@code voxel.ShapeRotation.quarterTurns}, which is package-private there; kept in lockstep with it deliberately rather than widening that package's API for one caller. */
    public static int quarterTurnsFromNorth(Direction facing) {
        Direction current = Direction.NORTH;
        for (int turns = 0; turns < 4; turns++) {
            if (current == facing) {
                return turns;
            }
            current = current.getClockWise();
        }
        return 0;
    }
}
