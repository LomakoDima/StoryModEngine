package com.dimalab.storymodengine.client.model.animator;

import com.dimalab.storymodengine.api.model.LayerBlendMode;
import com.dimalab.storymodengine.client.model.animator.expr.AnimationExpression;

import java.util.List;

/**
 * Poses named bones straight from expressions, with no clip behind them — see {@code
 * client.model.ProceduralLayer}. Default {@link LayerBlendMode#ADDITIVE}, matching HollowEngine —
 * deliberately different from {@link ClipAnimationLayerSpec}'s {@code OVERRIDE} default, since a
 * procedural gesture is meant to compose on top of whatever clip is already playing, not replace it.
 */
public record ProceduralLayerSpec(
        String id,
        List<ProceduralBoneTransformSpec> transforms,
        AnimationExpression weight,
        int priority,
        LayerBlendMode blendMode,
        BoneMask mask,
        float fadeIn,
        float fadeOut
) implements AnimatorLayerSpec {

    public ProceduralLayerSpec {
        transforms = List.copyOf(transforms);
    }

    public static ProceduralLayerSpec of(String id, List<ProceduralBoneTransformSpec> transforms) {
        return new ProceduralLayerSpec(id, transforms, AnimationExpression.ONE, 0, LayerBlendMode.ADDITIVE, BoneMask.FULL, 0f, 0f);
    }

    public ProceduralLayerSpec withWeight(AnimationExpression weight) {
        return new ProceduralLayerSpec(id, transforms, weight, priority, blendMode, mask, fadeIn, fadeOut);
    }

    public ProceduralLayerSpec withPriority(int priority) {
        return new ProceduralLayerSpec(id, transforms, weight, priority, blendMode, mask, fadeIn, fadeOut);
    }

    public ProceduralLayerSpec withBlendMode(LayerBlendMode blendMode) {
        return new ProceduralLayerSpec(id, transforms, weight, priority, blendMode, mask, fadeIn, fadeOut);
    }

    public ProceduralLayerSpec withMask(BoneMask mask) {
        return new ProceduralLayerSpec(id, transforms, weight, priority, blendMode, mask, fadeIn, fadeOut);
    }

    public ProceduralLayerSpec withFadeIn(float fadeIn) {
        return new ProceduralLayerSpec(id, transforms, weight, priority, blendMode, mask, fadeIn, fadeOut);
    }

    public ProceduralLayerSpec withFadeOut(float fadeOut) {
        return new ProceduralLayerSpec(id, transforms, weight, priority, blendMode, mask, fadeIn, fadeOut);
    }
}
