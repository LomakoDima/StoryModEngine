package com.dimalab.storymodengine.common.model;

import com.dimalab.storymodengine.common.model.gltf.GltfModelParser;
import com.dimalab.storymodengine.common.model.rig.ModelAliases;
import com.dimalab.storymodengine.common.model.rig.ModelRig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * The one place a model file becomes a finished {@link ModelDefinition}: parse, then apply whatever
 * its {@code .smemeta} sidecar asks for.
 *
 * <p>Both load paths go through here — the client's resource reload and the classpath read the server
 * uses for collision — so a model is rigged identically on both sides. Rigging at <b>load</b> rather
 * than at render time is what lets everything downstream stop caring that a rig exists: the definition
 * in the registry already has its hierarchy, so renderers, bounds and bone lookup all see one shape of
 * model. An earlier version rigged lazily behind a render-time cache, which meant the same model had
 * two forms depending on who asked for it.
 */
public final class ModelLoading {

    private ModelLoading() {
    }

    /** The sidecar's own id for a model: {@code …/player.glb} → {@code …/player.glb.smemeta}. */
    public static ResourceLocation metadataIdFor(ResourceLocation modelId) {
        return new ResourceLocation(modelId.getNamespace(), modelId.getPath() + ModelMetadata.EXTENSION);
    }

    /**
     * @param metadataBytes the sidecar's contents, or {@code null} when the model has none — which is
     *                      normal for a model that already carries its own hierarchy.
     */
    public static ModelDefinition load(ResourceLocation id, byte[] modelBytes, byte[] metadataBytes, ResourceManager resourceManager) {
        ModelMetadata metadata = metadataBytes == null
                ? ModelMetadata.EMPTY
                : ModelMetadataLoader.parse(metadataIdFor(id), metadataBytes);

        ModelDefinition definition = GltfModelParser.parse(id, modelBytes, resourceManager, metadata);
        if (metadata.hasAliases()) {
            definition = ModelAliases.apply(definition, metadata.aliases());
        }
        return metadata.hasRig() ? ModelRig.apply(definition, metadata.rig()) : definition;
    }
}
