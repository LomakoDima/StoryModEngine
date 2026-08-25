package com.dimalab.storymodengine.common.voxel;

import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;

/**
 * Rotates a {@link ShapeDefinition} authored facing {@link Direction#NORTH} (Minecraft's own
 * modeling convention) to face {@link Direction#EAST}/{@link Direction#SOUTH}/{@link
 * Direction#WEST} — the "don't hand-author four shapes" half of this package. Deliberately exact
 * 90°-step coordinate remapping (see {@link ShapeBox#rotatedQuarterTurns}) rather than a {@code
 * Quaternionf} from {@code com.dimalab.storymodengine.math}: a quaternion round-trip through {@code
 * Shapes.box}'s min/max corners risks floating-point drift off the block grid, and this system
 * never needs anything but exact 90° steps, so there's no interpolation to reuse from {@code math}
 * in the first place.
 *
 * <p>The turn count is however many times {@link Direction#getClockWise()} (Y-axis only — reused
 * directly, not reimplemented) reaches {@code facing} starting from {@code NORTH}, which is the
 * same direction a vanilla blockstate's {@code "y"} rotation turns a north-authored render model,
 * so a rotated collision/outline shape stays aligned with the rotated render.
 */
final class ShapeRotation {

    private ShapeRotation() {
    }

    static ShapeDefinition rotate(ShapeDefinition base, Direction facing) {
        int turns = quarterTurns(facing);
        if (turns == 0) {
            return base;
        }
        List<ShapeBox> rotated = new ArrayList<>(base.boxes().size());
        for (ShapeBox box : base.boxes()) {
            rotated.add(box.rotatedQuarterTurns(turns));
        }
        return ShapeDefinition.of(rotated);
    }

    /** How many clockwise quarter-turns from {@link Direction#NORTH} reach {@code facing}; 0 for a non-horizontal direction. */
    static int quarterTurns(Direction facing) {
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
