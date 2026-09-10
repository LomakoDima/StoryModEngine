package com.dimalab.storymodengine.client.model.animator.expr;

import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;

/**
 * Everything an animation expression can read, refreshed once per frame by whatever poses the model
 * ({@code GltfModelLayer}/{@code ModelInstance}) before the layer stack samples. One instance lives
 * per {@code ModelInstance}, reused every frame rather than rebuilt — the fields are just scratch
 * state, not identity.
 *
 * <p>Plain public mutable fields, matching this codebase's existing convention for per-frame POD
 * objects ({@code BonePose}, {@code AnimationData}) — not a shortcut, the established idiom here.
 */
public final class AnimEvalContext {

    /** Null for a no-entity case (e.g. a model preview) — {@code query.*} entity readings fall back to {@link #variables} then. */
    public LivingEntity entity;

    public float partialTick;
    public float gameTime;
    public float time;
    public float deltaTime;

    /** Written by {@code SpecLayer}/{@code AnimationController} immediately before evaluating that layer's own expressions. */
    public float layerTime;
    public float layerAge;
    public float layerWeight;
    public float stateTime;

    public float horizontalSpeed;
    public float localForwardSpeed;
    public float localSideSpeed;
    public float signedHorizontalSpeed;

    public float bodyYaw;
    public float headYaw;
    public float headPitch;
    public float headBodyYawDelta;

    /** {@code variable.*}/{@code v.*} — persists across frames, set by gameplay Java code. */
    public final Map<String, Float> variables = new HashMap<>();

    /** {@code temp.*}/{@code t.*} — scratch space, cleared once per frame by {@code ModelInstance.poseWith}. */
    public final Map<String, Float> temporaries = new HashMap<>();

    /** {@code data.*}/{@code d.*} — read-only per-entity numeric snapshot; no SME producer exists yet, so this stays empty. */
    public Map<String, Float> data = Map.of();

    /** Value of an entity-derived query name when {@link #entity} is null — falls back to a same-named variable, defaulting to zero. */
    float override(String name) {
        Float value = variables.get(name);
        return value != null ? value : 0f;
    }
}
