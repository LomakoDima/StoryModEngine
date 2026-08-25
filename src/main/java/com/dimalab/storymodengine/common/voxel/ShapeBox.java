package com.dimalab.storymodengine.common.voxel;

/**
 * One cuboid, in normalized {@code [0, 1]} block-space coordinates — what {@link
 * net.minecraft.world.phys.shapes.Shapes#box} actually wants. Model JSON and {@link
 * ShapeDefinition.Builder} both speak in Minecraft's own model units ({@code 0..16}); {@link
 * #fromModelUnits} is the one place that {@code /16} conversion happens, so it's never silently
 * redone (or gotten slightly wrong) at a second call site.
 */
public record ShapeBox(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {

    private static final double MODEL_UNIT = 16.0;

    /** {@code from}/{@code to} in model units (0..16), e.g. {@code [0,0,0]} to {@code [16,8,16]}. */
    public static ShapeBox fromModelUnits(double fromX, double fromY, double fromZ, double toX, double toY, double toZ) {
        return new ShapeBox(fromX / MODEL_UNIT, fromY / MODEL_UNIT, fromZ / MODEL_UNIT,
                toX / MODEL_UNIT, toY / MODEL_UNIT, toZ / MODEL_UNIT);
    }

    /**
     * This box rotated {@code turns} quarter-steps clockwise (as viewed from above) around the
     * block center {@code (0.5, 0.5)}, remapping the normalized X/Z corners directly rather than
     * through a quaternion — see {@code ShapeRotation}'s class doc for why exactness here matters.
     * Y is untouched; only horizontal facing is supported.
     */
    ShapeBox rotatedQuarterTurns(int turns) {
        int steps = ((turns % 4) + 4) % 4;
        double x1 = minX, z1 = minZ, x2 = maxX, z2 = maxZ;
        for (int i = 0; i < steps; i++) {
            double nx1 = 1 - z1, nz1 = x1;
            double nx2 = 1 - z2, nz2 = x2;
            x1 = nx1;
            z1 = nz1;
            x2 = nx2;
            z2 = nz2;
        }
        return new ShapeBox(Math.min(x1, x2), minY, Math.min(z1, z2), Math.max(x1, x2), maxY, Math.max(z1, z2));
    }
}
