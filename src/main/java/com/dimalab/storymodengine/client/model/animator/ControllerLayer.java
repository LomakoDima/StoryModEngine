package com.dimalab.storymodengine.client.model.animator;

import com.dimalab.storymodengine.client.model.AnimationPose;
import com.dimalab.storymodengine.client.model.animator.expr.AnimEvalContext;

import java.util.Set;

/** Wraps an {@link AnimationController} as another {@link AnimationLayer}. */
public final class ControllerLayer extends SpecLayer {

    private final AnimationController controller;

    public ControllerLayer(AnimationControllerLayerSpec spec) {
        super(spec);
        this.controller = new AnimationController(spec);
    }

    @Override
    public float time() {
        return controller.stateTime();
    }

    @Override
    protected boolean accepts(AnimatorLayerSpec spec) {
        return spec instanceof AnimationControllerLayerSpec;
    }

    @Override
    protected void onReconfigured(AnimatorLayerSpec spec) {
        controller.configure((AnimationControllerLayerSpec) spec);
    }

    @Override
    public LayerPose sample(PoseTarget target, AnimEvalContext context) {
        Set<Integer> allowed = mask(target);
        AnimationPose pose = controller.sample(target, allowed, context);
        return pose == null ? null : new LayerPose(pose);
    }
}
