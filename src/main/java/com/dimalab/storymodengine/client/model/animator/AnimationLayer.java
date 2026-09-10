package com.dimalab.storymodengine.client.model.animator;

import com.dimalab.storymodengine.api.model.LayerBlendMode;
import com.dimalab.storymodengine.client.model.animator.expr.AnimEvalContext;

/**
 * One contributor to a model's pose. {@code ModelAnimator} stacks these by {@link #priority} and
 * blends them in, highest priority applied last.
 *
 * <p>The concrete implementations are {@link ClipLayer} (plays one named clip), {@link
 * ProceduralLayer} (poses named bones straight from expressions, no clip), and {@link
 * ControllerLayer} (a finite state machine over clips, wrapping {@link AnimationController}).
 */
public interface AnimationLayer {

    String id();

    default int priority() {
        return 0;
    }

    default LayerBlendMode blendMode() {
        return LayerBlendMode.OVERRIDE;
    }

    /** True once the layer has nothing left to do and can be dropped — a finished one-shot clip past its fade-out. */
    default boolean finished() {
        return false;
    }

    /** How strongly the layer contributes; at or below zero, {@link #sample} is never even called. */
    default float weight(AnimEvalContext context) {
        return 1f;
    }

    /** Null unless this layer is playing a single named clip — an escape hatch so a caller can ask "what clip is this?" without casting to a concrete layer type. */
    default String clipName() {
        return null;
    }

    /** Null when the layer has nothing to contribute this frame. */
    LayerPose sample(PoseTarget target, AnimEvalContext context);

    /** Builds the concrete layer for whichever spec type this is. */
    static AnimationLayer forSpec(AnimatorLayerSpec spec) {
        if (spec instanceof ClipAnimationLayerSpec clipSpec) {
            return new ClipLayer(clipSpec);
        }
        if (spec instanceof AnimationControllerLayerSpec controllerSpec) {
            return new ControllerLayer(controllerSpec);
        }
        if (spec instanceof ProceduralLayerSpec proceduralSpec) {
            return new ProceduralLayer(proceduralSpec);
        }
        throw new IllegalArgumentException("unhandled spec type: " + spec.getClass());
    }
}
