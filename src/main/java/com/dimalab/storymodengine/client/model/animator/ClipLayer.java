package com.dimalab.storymodengine.client.model.animator;

import com.dimalab.storymodengine.client.model.AnimationPose;
import com.dimalab.storymodengine.client.model.animator.expr.AnimEvalContext;
import com.dimalab.storymodengine.client.model.animator.expr.AnimExpr;
import com.dimalab.storymodengine.common.model.AnimationClip;

import java.util.Set;

/** Plays one named clip — the direct replacement for this project's old, single concrete {@code AnimationLayer} class. */
public final class ClipLayer extends SpecLayer {

    private final ClipPlayback playback = new ClipPlayback();
    private boolean finished = false;

    public ClipLayer(ClipAnimationLayerSpec spec) {
        super(spec);
    }

    private ClipAnimationLayerSpec clipSpec() {
        return (ClipAnimationLayerSpec) spec();
    }

    @Override
    public float time() {
        return playback.time();
    }

    @Override
    public boolean finished() {
        return finished;
    }

    @Override
    public String clipName() {
        return clipSpec().animation();
    }

    @Override
    protected boolean accepts(AnimatorLayerSpec spec) {
        return spec instanceof ClipAnimationLayerSpec;
    }

    @Override
    public LayerPose sample(PoseTarget target, AnimEvalContext context) {
        ClipAnimationLayerSpec spec = clipSpec();
        AnimationClip animation = target.animation(spec.animation());
        if (animation == null) {
            return null;
        }
        float speed = AnimExpr.evalFloat(spec.speed(), context, 1f);
        float sampleTime = playback.advance(animation.duration(), spec.playMode(), speed, context.deltaTime);

        float fadeOut = fadeOutScale(spec);
        if (playback.ended() && spec.removeOnEnd() && fadeOut <= 0f) {
            finished = true;
            return null;
        }

        Set<Integer> allowed = mask(target);
        AnimationPose reference = null;
        if (spec.referencePose() != null) {
            AnimationClip referenceClip = target.animation(spec.referencePose());
            if (referenceClip != null) {
                reference = AnimationPose.sample(referenceClip, 0f, allowed);
            }
        }
        return new LayerPose(AnimationPose.sample(animation, sampleTime, allowed), fadeOut, reference);
    }

    /** Only ever less than 1 for a finished {@link AnimationPlayMode#ONCE} clip with {@code fadeOut > 0} — scales this frame's weight down to 0 over {@code fadeOut} seconds past the end. */
    private float fadeOutScale(ClipAnimationLayerSpec spec) {
        if (spec.playMode() != AnimationPlayMode.ONCE || spec.fadeOut() <= 0f || !playback.ended()) {
            return 1f;
        }
        return Math.min(1f, Math.max(0f, 1f - playback.endElapsed() / spec.fadeOut()));
    }
}
