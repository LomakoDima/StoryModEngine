package com.dimalab.storymodengine.client.model;

import com.dimalab.storymodengine.client.model.animator.AnimationLayer;
import com.dimalab.storymodengine.client.model.animator.LayerPose;
import com.dimalab.storymodengine.client.model.animator.PoseTarget;
import com.dimalab.storymodengine.client.model.animator.expr.AnimEvalContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The layer stack for one model instance. Posing a frame is always the same three steps: reset every
 * node to its bind pose, then let each layer compose onto it in priority order, then recompute the
 * hierarchy's matrices once. Resetting first is what lets a bone that stopped being animated fall
 * back to rest instead of freezing wherever the last clip left it.
 */
public final class ModelAnimator {

    private final List<AnimationLayer> layers = new ArrayList<>();

    public List<AnimationLayer> layers() {
        return layers;
    }

    public AnimationLayer addLayer(AnimationLayer layer) {
        layers.add(layer);
        return layer;
    }

    /** Null if no layer with that id is currently in the stack. */
    public AnimationLayer layer(String id) {
        for (AnimationLayer layer : layers) {
            if (layer.id().equals(id)) {
                return layer;
            }
        }
        return null;
    }

    public void removeLayer(String id) {
        layers.removeIf(l -> l.id().equals(id));
    }

    public void clear() {
        layers.clear();
    }

    /**
     * Applies every layer onto {@code target}'s nodes, highest-{@code priority} layer applied last
     * (ties keep insertion order — {@link List#sort} is stable), then drops any layer that reports
     * {@link AnimationLayer#finished()} — a one-shot clip past its fade-out cleans itself up rather
     * than lingering in the stack forever. Caller is responsible for having reset the pose first —
     * {@code ModelInstance.pose} does both.
     */
    public void applyTo(PoseTarget target, AnimEvalContext context) {
        if (layers.isEmpty()) {
            return;
        }
        List<AnimationLayer> ordered = new ArrayList<>(layers);
        ordered.sort(Comparator.comparingInt(AnimationLayer::priority));
        for (AnimationLayer layer : ordered) {
            float weight = layer.weight(context);
            if (weight <= 0f) {
                continue;
            }
            LayerPose sampled = layer.sample(target, context);
            if (sampled == null) {
                continue;
            }
            float finalWeight = weight * sampled.weightScale;
            if (finalWeight <= 0f) {
                continue;
            }
            sampled.pose.applyTo(target.nodesByIndex(), layer.blendMode(), finalWeight, sampled.reference);
        }
        layers.removeIf(AnimationLayer::finished);
    }
}
