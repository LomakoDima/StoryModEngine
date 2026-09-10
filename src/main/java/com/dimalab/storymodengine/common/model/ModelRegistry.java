package com.dimalab.storymodengine.common.model;

import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code id -> ModelDefinition}, mirroring {@code dialogue.registry.DialogueRegistry}/{@code
 * cinematic.registry.CutsceneRegistry}. Only holds models that have actually been <b>loaded</b> —
 * parsed, geometry and all — which since {@code reload.ModelReloadListener} stopped doing that
 * eagerly happens on demand (see {@code client.model.LazyModelLoader}) rather than wholesale on every
 * client resource reload. {@link #knownIds} is the separate, much cheaper set of ids a reload
 * <i>discovered</i> exist, whether or not they've been loaded — kept apart from {@link #MODELS} itself
 * so "what's available" and "what's actually parsed right now" stay two different questions with two
 * different costs to answer.
 *
 * <p>{@link #reset()} fires {@link #onReset} first, which is how {@code
 * client.model.ModelTextureLoader} learns to free the GPU textures belonging to the models about to
 * be discarded — a plain callback rather than a direct call, so this common-side class never
 * references the client-only texture code.
 */
public final class ModelRegistry {

    private static final Map<ResourceLocation, ModelDefinition> MODELS = new ConcurrentHashMap<>();
    private static volatile Set<ResourceLocation> knownIds = Set.of();
    private static volatile Runnable onReset;

    private ModelRegistry() {
    }

    /** Registered once, client-side, by {@code ModelTextureLoader}'s own bootstrap. */
    public static void onReset(Runnable callback) {
        onReset = callback;
    }

    public static void reset() {
        Runnable callback = onReset;
        if (callback != null) {
            callback.run();
        }
        MODELS.clear();
    }

    /** Every model id the last reload found on disk, loaded or not — see {@code reload.ModelReloadListener}. */
    public static void setKnownIds(Collection<ResourceLocation> ids) {
        knownIds = Set.copyOf(ids);
    }

    /** Every model id known to exist, whether or not it has actually been loaded yet — for enumeration ({@code /sme model list}), not resolution. */
    public static Set<ResourceLocation> knownIds() {
        return knownIds;
    }

    public static void register(ModelDefinition definition) {
        MODELS.put(definition.id(), definition);
    }

    public static ModelDefinition get(ResourceLocation id) {
        return MODELS.get(id);
    }

    public static Collection<ModelDefinition> all() {
        return MODELS.values();
    }
}
