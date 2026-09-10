package com.dimalab.storymodengine.common.voxel;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The public entry point to this package — everything else ({@link ModelShapeLoader}, {@link
 * ShapeRotation}, {@link ShapeCache}) is an implementation detail behind these four static
 * methods.
 *
 * <pre>{@code
 * VoxelShape shape = ModelShapes.get("mymod:block/machine", state.getValue(FACING));
 * }</pre>
 */
public final class ModelShapes {

    private ModelShapes() {
    }

    /** The parsed, unrotated {@link ShapeDefinition} for a model — parsed once, cached after. */
    public static ShapeDefinition load(ResourceLocation modelId) {
        return ShapeCache.definition(modelId);
    }

    public static ShapeDefinition load(String modelId) {
        return load(new ResourceLocation(modelId));
    }

    /** The final, cached {@link VoxelShape} for a model rotated to {@code facing}. */
    public static VoxelShape get(ResourceLocation modelId, Direction facing) {
        return ShapeCache.shape(modelId, facing);
    }

    public static VoxelShape get(String modelId, Direction facing) {
        return get(new ResourceLocation(modelId), facing);
    }

    /**
     * Drops every cached definition and shape, so the next {@link #load}/{@link #get} reparses
     * from the classpath. Not needed in normal use (models don't change while the game is
     * running) — exposed for diagnostics such as {@code /sme voxel test}, which uses it
     * to verify parsing is deterministic independent of cache identity.
     */
    public static void clearCache() {
        ShapeCache.clear();
    }
}
