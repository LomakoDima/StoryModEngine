package com.dimalab.storymodengine.common.model.physics;

import com.dimalab.storymodengine.common.StoryModEngine;
import com.dimalab.storymodengine.common.logging.EngineLog;
import com.dimalab.storymodengine.common.model.ModelDefinition;
import com.dimalab.storymodengine.common.model.ModelRegistry;
import com.dimalab.storymodengine.common.model.ModelLoading;
import com.dimalab.storymodengine.common.model.gltf.ExternalModelIO;
import com.dimalab.storymodengine.common.voxel.ShapeDefinition;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The physical facade over a loaded model: its {@link ModelBounds} (an entity's hitbox) and its
 * collision {@link ShapeDefinition} (a block's shape). Both are derived once per model id and cached,
 * because both walk every triangle in the mesh and neither changes while the game runs.
 *
 * <p><b>Works on both sides, which is the whole point.</b> Collision has to be known to the server or
 * it isn't real — the server is what accepts or rejects a player's movement. But models live under
 * {@code assets/}, which the server's own {@code ResourceManager} never exposes. So this resolves a
 * model in two steps: the client's {@link ModelRegistry} first (already populated by the resource
 * reload), then a direct read from the mod jar's classpath, which is reachable from both sides
 * because the jar itself is installed on both. The server therefore parses geometry for itself
 * — materials and textures simply fail to resolve there and are logged, which costs nothing since
 * the server never draws anything.
 */
public final class ModelPhysics {

    private static final String DIRECTORY = "storymodengine/models";
    private static final String[] EXTENSIONS = {".gltf", ".glb"};

    private static final Map<ResourceLocation, ModelDefinition> CLASSPATH_CACHE = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, ModelBounds> BOUNDS_CACHE = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, ModelBounds> FOOTPRINT_CACHE = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, Map<Integer, ModelBounds>> PER_NODE_BOUNDS_CACHE = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, ShapeDefinition> SHAPE_CACHE = new ConcurrentHashMap<>();

    private ModelPhysics() {
    }

    /** Resolves {@code name} (a bare model name, as the commands use) to its full resource id. */
    public static ResourceLocation idFor(String name, String extension) {
        return new ResourceLocation(StoryModEngine.MODID, DIRECTORY + "/" + name + extension);
    }

    /** The model behind a bare name, from the client registry if loaded, else parsed off the classpath. Null when no such file exists on either path. */
    public static ModelDefinition resolve(String name) {
        for (String extension : EXTENSIONS) {
            ResourceLocation id = idFor(name, extension);
            ModelDefinition registered = ModelRegistry.get(id);
            if (registered != null) {
                return registered;
            }
            ModelDefinition fromClasspath = fromClasspath(id);
            if (fromClasspath != null) {
                return fromClasspath;
            }
        }
        return null;
    }

    private static ModelDefinition fromClasspath(ResourceLocation id) {
        ModelDefinition cached = CLASSPATH_CACHE.get(id);
        if (cached != null) {
            return cached;
        }
        byte[] bytes = readClasspath(id);
        if (bytes == null) {
            return null;
        }
        try {
            // No ResourceManager: GltfBufferResolver falls back to the classpath for any sibling
            // .bin/texture, which is what makes this work server-side. The sidecar is read the same
            // way, so a rigged model is rigged identically on both sides.
            ModelDefinition definition = ModelLoading.load(id, bytes, readClasspath(ModelLoading.metadataIdFor(id)), null);
            CLASSPATH_CACHE.put(id, definition);
            return definition;
        } catch (Exception e) {
            EngineLog.channel("Model").error("Failed to parse " + id + " from the classpath", e);
            return null;
        }
    }

    private static byte[] readClasspath(ResourceLocation id) {
        // Checked first — a server has no ResourceManager to prefer an external override through,
        // so this is the one place server-side reads can see it at all. See ExternalModelIO.
        byte[] external = ExternalModelIO.read(id);
        if (external != null) {
            return external;
        }
        String path = "/assets/" + id.getNamespace() + "/" + id.getPath();
        try (InputStream in = ModelPhysics.class.getResourceAsStream(path)) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException e) {
            EngineLog.channel("Model").error("Failed to read " + path + " from the classpath", e);
            return null;
        }
    }

    /** Bind-pose extent of a model, cached. {@link ModelBounds#EMPTY} when the model can't be resolved. */
    public static ModelBounds bounds(String name) {
        ModelDefinition definition = resolve(name);
        if (definition == null) {
            return ModelBounds.EMPTY;
        }
        return BOUNDS_CACHE.computeIfAbsent(definition.id(), k -> ModelBounds.of(definition));
    }

    /** Same as {@link #bounds}, but excluding whatever the model's own sidecar names — see {@link ModelBounds#footprint}. Cached separately since it's a distinct measurement. */
    public static ModelBounds footprint(String name) {
        ModelDefinition definition = resolve(name);
        if (definition == null) {
            return ModelBounds.EMPTY;
        }
        return FOOTPRINT_CACHE.computeIfAbsent(definition.id(), k -> ModelBounds.footprint(definition));
    }

    /**
     * Each meshed node's own local extent, cached per model id — see {@link ModelBounds#perNode} for
     * why this is separate from {@link #bounds}. Takes a resolved {@link ModelDefinition} directly
     * rather than a bare name: every caller of this (currently just {@code
     * client.model.ModelInstance#worldCullingBox}) already has one on hand, since it's building on an
     * already-constructed instance rather than resolving a model by name from scratch.
     */
    public static Map<Integer, ModelBounds> perNodeBounds(ModelDefinition definition) {
        return PER_NODE_BOUNDS_CACHE.computeIfAbsent(definition.id(), id -> ModelBounds.perNode(definition));
    }

    /** Block collision shape for a model, voxelized and fitted to the unit cube, cached. */
    public static ShapeDefinition blockShape(String name) {
        ModelDefinition definition = resolve(name);
        if (definition == null) {
            return ShapeDefinition.EMPTY;
        }
        return SHAPE_CACHE.computeIfAbsent(definition.id(), k -> ModelVoxelizer.forBlock(definition));
    }

    /** Drops every cached derivation — called when the model registry is reset, so a reloaded model re-derives its physics. */
    public static void clearCache() {
        CLASSPATH_CACHE.clear();
        BOUNDS_CACHE.clear();
        FOOTPRINT_CACHE.clear();
        PER_NODE_BOUNDS_CACHE.clear();
        SHAPE_CACHE.clear();
    }
}
