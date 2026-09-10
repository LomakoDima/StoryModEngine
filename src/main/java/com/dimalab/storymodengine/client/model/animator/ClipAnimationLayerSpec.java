package com.dimalab.storymodengine.client.model.animator;

import com.dimalab.storymodengine.api.model.LayerBlendMode;
import com.dimalab.storymodengine.client.model.animator.expr.AnimationExpression;

/**
 * Plays one named clip. {@code referencePose}, when set, names another clip sampled at time 0 to
 * blend an {@link LayerBlendMode#ADDITIVE} layer's delta against instead of the bind pose — see
 * {@code AnimationPose.applyTo}'s 4-arg overload.
 */
public record ClipAnimationLayerSpec(
        String id,
        String animation,
        AnimationPlayMode playMode,
        AnimationExpression speed,
        AnimationExpression weight,
        int priority,
        LayerBlendMode blendMode,
        BoneMask mask,
        float fadeIn,
        float fadeOut,
        String referencePose,
        boolean removeOnEnd
) implements AnimatorLayerSpec {

    /** {@code removeOnEnd} defaults to {@code playMode == ONCE}, matching HollowEngine's own default. */
    public static ClipAnimationLayerSpec of(String id, String animation) {
        return new ClipAnimationLayerSpec(id, animation, AnimationPlayMode.ONCE, AnimationExpression.ONE,
                AnimationExpression.ONE, 0, LayerBlendMode.OVERRIDE, BoneMask.FULL, 0f, 0f, null, true);
    }

    public ClipAnimationLayerSpec withPlayMode(AnimationPlayMode playMode) {
        return new ClipAnimationLayerSpec(id, animation, playMode, speed, weight, priority, blendMode, mask, fadeIn, fadeOut, referencePose, removeOnEnd);
    }

    public ClipAnimationLayerSpec withSpeed(AnimationExpression speed) {
        return new ClipAnimationLayerSpec(id, animation, playMode, speed, weight, priority, blendMode, mask, fadeIn, fadeOut, referencePose, removeOnEnd);
    }

    public ClipAnimationLayerSpec withWeight(AnimationExpression weight) {
        return new ClipAnimationLayerSpec(id, animation, playMode, speed, weight, priority, blendMode, mask, fadeIn, fadeOut, referencePose, removeOnEnd);
    }

    public ClipAnimationLayerSpec withPriority(int priority) {
        return new ClipAnimationLayerSpec(id, animation, playMode, speed, weight, priority, blendMode, mask, fadeIn, fadeOut, referencePose, removeOnEnd);
    }

    public ClipAnimationLayerSpec withBlendMode(LayerBlendMode blendMode) {
        return new ClipAnimationLayerSpec(id, animation, playMode, speed, weight, priority, blendMode, mask, fadeIn, fadeOut, referencePose, removeOnEnd);
    }

    public ClipAnimationLayerSpec withMask(BoneMask mask) {
        return new ClipAnimationLayerSpec(id, animation, playMode, speed, weight, priority, blendMode, mask, fadeIn, fadeOut, referencePose, removeOnEnd);
    }

    public ClipAnimationLayerSpec withFadeIn(float fadeIn) {
        return new ClipAnimationLayerSpec(id, animation, playMode, speed, weight, priority, blendMode, mask, fadeIn, fadeOut, referencePose, removeOnEnd);
    }

    public ClipAnimationLayerSpec withFadeOut(float fadeOut) {
        return new ClipAnimationLayerSpec(id, animation, playMode, speed, weight, priority, blendMode, mask, fadeIn, fadeOut, referencePose, removeOnEnd);
    }

    public ClipAnimationLayerSpec withReferencePose(String referencePose) {
        return new ClipAnimationLayerSpec(id, animation, playMode, speed, weight, priority, blendMode, mask, fadeIn, fadeOut, referencePose, removeOnEnd);
    }

    public ClipAnimationLayerSpec withRemoveOnEnd(boolean removeOnEnd) {
        return new ClipAnimationLayerSpec(id, animation, playMode, speed, weight, priority, blendMode, mask, fadeIn, fadeOut, referencePose, removeOnEnd);
    }
}
