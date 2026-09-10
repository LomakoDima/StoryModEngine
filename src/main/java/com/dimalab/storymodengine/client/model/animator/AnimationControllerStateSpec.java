package com.dimalab.storymodengine.client.model.animator;

import com.dimalab.storymodengine.client.model.animator.expr.AnimationExpression;

/** One state of an {@link AnimationControllerLayerSpec}'s state machine: which clip it plays, and how. */
public record AnimationControllerStateSpec(String id, String animation, AnimationPlayMode playMode, AnimationExpression speed) {

    public static AnimationControllerStateSpec of(String id, String animation) {
        return new AnimationControllerStateSpec(id, animation, AnimationPlayMode.LOOP, AnimationExpression.ONE);
    }

    public AnimationControllerStateSpec withPlayMode(AnimationPlayMode playMode) {
        return new AnimationControllerStateSpec(id, animation, playMode, speed);
    }

    public AnimationControllerStateSpec withSpeed(AnimationExpression speed) {
        return new AnimationControllerStateSpec(id, animation, playMode, speed);
    }
}
