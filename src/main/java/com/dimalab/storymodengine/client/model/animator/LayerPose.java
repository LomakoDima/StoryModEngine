package com.dimalab.storymodengine.client.model.animator;

import com.dimalab.storymodengine.client.model.AnimationPose;

/** What one layer contributes for this frame: a pose, an extra weight scale (e.g. clip fade-out), and an optional reference pose to blend an additive layer's delta against. */
public final class LayerPose {

    public final AnimationPose pose;
    public final float weightScale;
    public final AnimationPose reference;

    public LayerPose(AnimationPose pose) {
        this(pose, 1f, null);
    }

    public LayerPose(AnimationPose pose, float weightScale, AnimationPose reference) {
        this.pose = pose;
        this.weightScale = weightScale;
        this.reference = reference;
    }
}
