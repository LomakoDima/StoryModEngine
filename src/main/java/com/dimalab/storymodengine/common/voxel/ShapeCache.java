package com.dimalab.storymodengine.common.voxel;

import com.dimalab.storymodengine.common.logging.EngineLog;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Two layers of cache so a model is parsed at most once and each (model, rotation) pair is unioned
 * into a {@link VoxelShape} at most once, no matter how many times {@code Block#getShape()} calls
 * in: the raw {@link ShapeDefinition} (unrotated, straight from {@link ModelShapeLoader}), and the
 * final rotated {@link VoxelShape} keyed on every input that affects it — model id and rotation.
 * {@code Block#getShape()} can run from world-generation/chunk-loading threads as well as the main
 * thread, so both maps are {@link ConcurrentHashMap}s.
 */
final class ShapeCache {

    private record RotatedKey(ResourceLocation model, Direction rotation) {
    }

    private static final Map<ResourceLocation, ShapeDefinition> DEFINITIONS = new ConcurrentHashMap<>();
    private static final Map<RotatedKey, VoxelShape> ROTATED_SHAPES = new ConcurrentHashMap<>();

    private ShapeCache() {
    }

    static ShapeDefinition definition(ResourceLocation modelId) {
        return DEFINITIONS.computeIfAbsent(modelId, id -> {
            EngineLog.channel("Voxel").debug("Loading model {} (cache miss).", id);
            return ModelShapeLoader.load(id);
        });
    }

    static VoxelShape shape(ResourceLocation modelId, Direction rotation) {
        return ROTATED_SHAPES.computeIfAbsent(new RotatedKey(modelId, rotation),
                key -> definition(key.model()).rotated(key.rotation()).toVoxelShape());
    }

    static void clear() {
        DEFINITIONS.clear();
        ROTATED_SHAPES.clear();
    }
}
