package com.dimalab.storymodengine.client.model.animator;

import com.dimalab.storymodengine.api.model.LayerBlendMode;
import com.dimalab.storymodengine.client.model.animator.expr.AnimationExpression;

import java.util.List;

/** A finite state machine over clips — see {@link AnimationController} for the runtime that drives it. */
public record AnimationControllerLayerSpec(
        String id,
        List<AnimationControllerStateSpec> states,
        List<AnimationControllerTransitionSpec> transitions,
        String entryState,
        AnimationExpression weight,
        int priority,
        LayerBlendMode blendMode,
        BoneMask mask,
        float fadeIn,
        float fadeOut
) implements AnimatorLayerSpec {

    public AnimationControllerLayerSpec {
        states = List.copyOf(states);
        transitions = List.copyOf(transitions);
    }

    public static AnimationControllerLayerSpec of(String id, List<AnimationControllerStateSpec> states, List<AnimationControllerTransitionSpec> transitions) {
        String entryState = states.isEmpty() ? null : states.get(0).id();
        return new AnimationControllerLayerSpec(id, states, transitions, entryState, AnimationExpression.ONE,
                0, LayerBlendMode.OVERRIDE, BoneMask.FULL, 0f, 0f);
    }

    public AnimationControllerLayerSpec withEntryState(String entryState) {
        return new AnimationControllerLayerSpec(id, states, transitions, entryState, weight, priority, blendMode, mask, fadeIn, fadeOut);
    }

    public AnimationControllerLayerSpec withWeight(AnimationExpression weight) {
        return new AnimationControllerLayerSpec(id, states, transitions, entryState, weight, priority, blendMode, mask, fadeIn, fadeOut);
    }

    public AnimationControllerLayerSpec withPriority(int priority) {
        return new AnimationControllerLayerSpec(id, states, transitions, entryState, weight, priority, blendMode, mask, fadeIn, fadeOut);
    }

    public AnimationControllerLayerSpec withBlendMode(LayerBlendMode blendMode) {
        return new AnimationControllerLayerSpec(id, states, transitions, entryState, weight, priority, blendMode, mask, fadeIn, fadeOut);
    }

    public AnimationControllerLayerSpec withMask(BoneMask mask) {
        return new AnimationControllerLayerSpec(id, states, transitions, entryState, weight, priority, blendMode, mask, fadeIn, fadeOut);
    }

    public AnimationControllerLayerSpec withFadeIn(float fadeIn) {
        return new AnimationControllerLayerSpec(id, states, transitions, entryState, weight, priority, blendMode, mask, fadeIn, fadeOut);
    }

    public AnimationControllerLayerSpec withFadeOut(float fadeOut) {
        return new AnimationControllerLayerSpec(id, states, transitions, entryState, weight, priority, blendMode, mask, fadeIn, fadeOut);
    }
}
