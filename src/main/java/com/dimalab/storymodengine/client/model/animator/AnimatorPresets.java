package com.dimalab.storymodengine.client.model.animator;

import com.dimalab.storymodengine.common.logging.EngineLog;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Named, Java-built animator layer bundles — a {@code .smemeta}'s {@code "animationController"} names
 * one of these by id, and {@link com.dimalab.storymodengine.client.model.ModelInstance}'s constructor
 * attaches every spec the factory returns. Unlike {@link com.dimalab.storymodengine.client.model.animator.expr.QueryTable}'s
 * frozen table, this stays open: more presets are expected later, so {@link #register} is public.
 *
 * <p>A factory is called once per attaching {@code ModelInstance} — every instance gets its own fresh
 * layer objects (with their own playback state), never a shared one.
 */
public final class AnimatorPresets {

    private static final Map<String, Supplier<List<AnimatorLayerSpec>>> REGISTRY = new ConcurrentHashMap<>();

    static {
        register(StandardPlayerAnimatorPreset.ID, StandardPlayerAnimatorPreset::create);
    }

    private AnimatorPresets() {
    }

    public static void register(String id, Supplier<List<AnimatorLayerSpec>> factory) {
        REGISTRY.put(id, factory);
    }

    /** An empty list — logged once — for an unknown id, so a bad {@code .smemeta} value costs a model its controller, not its load. */
    public static List<AnimatorLayerSpec> get(String id) {
        Supplier<List<AnimatorLayerSpec>> factory = REGISTRY.get(id);
        if (factory == null) {
            EngineLog.channel("Model").warn("Unknown animation controller preset '{}' — model will load with no attached layers", id);
            return List.of();
        }
        return factory.get();
    }
}
