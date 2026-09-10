package com.dimalab.storymodengine.client.model.animator;

import com.dimalab.storymodengine.api.model.LayerBlendMode;
import com.dimalab.storymodengine.client.model.animator.expr.AnimEvalContext;
import com.dimalab.storymodengine.client.model.animator.expr.AnimExpr;

import java.util.Set;

/**
 * A layer described by an {@link AnimatorLayerSpec}: it takes its priority, weight expression,
 * fade-in and mask from the spec, tracks how long it's existed (for fade-in), and leaves only
 * sampling itself to the subclass.
 */
public abstract class SpecLayer implements AnimationLayer {

    private AnimatorLayerSpec spec;
    private float age = 0f;

    protected SpecLayer(AnimatorLayerSpec initialSpec) {
        this.spec = initialSpec;
    }

    protected AnimatorLayerSpec spec() {
        return spec;
    }

    @Override
    public String id() {
        return spec.id();
    }

    @Override
    public int priority() {
        return spec.priority();
    }

    @Override
    public LayerBlendMode blendMode() {
        return spec.blendMode();
    }

    /** Where this layer is in its own clip/state, for {@code query.layer_time} — {@code 0} unless a subclass tracks a clock ({@link ClipLayer}, {@link ControllerLayer}). */
    public float time() {
        return 0f;
    }

    /** Applies a new description without discarding this layer's own playback — {@code false} (no change made) if {@code next} names a different id or the subclass rejects its type. */
    public boolean reconfigure(AnimatorLayerSpec next) {
        if (!next.id().equals(spec.id()) || !accepts(next)) {
            return false;
        }
        spec = next;
        onReconfigured(next);
        return true;
    }

    @Override
    public float weight(AnimEvalContext context) {
        age += Math.max(0f, context.deltaTime);
        context.layerAge = age;
        context.layerTime = time();
        float declared = clamp01(AnimExpr.evalFloat(spec.weight(), context, 1f));
        context.layerWeight = declared;
        float fadeIn = spec.fadeIn() <= 0f ? 1f : clamp01(age / spec.fadeIn());
        return declared * fadeIn;
    }

    protected abstract boolean accepts(AnimatorLayerSpec spec);

    protected void onReconfigured(AnimatorLayerSpec spec) {
    }

    protected Set<Integer> mask(PoseTarget target) {
        return target.mask(spec.mask());
    }

    protected static float clamp01(float value) {
        return Math.min(1f, Math.max(0f, value));
    }
}
