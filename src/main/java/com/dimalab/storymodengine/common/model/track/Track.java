package com.dimalab.storymodengine.common.model.track;

import java.util.Arrays;

/**
 * One animated property's keyframes over time. Sampling is {@code O(log n)} — a binary search over
 * the sorted {@code times} array, not a linear scan; a rig with a few hundred keyframes per bone is
 * sampled once per bone per frame, so the difference is real, not theoretical.
 *
 * <p><b>Values are stored as deltas from the node's bind pose</b>, never as the absolute values glTF
 * itself carries — see {@code gltf.GltfAnimationImporter} for the conversion and MODEL_SYSTEM_DESIGN.md's
 * animation section for why. Everything downstream (pose blending, additive layers, partial weights)
 * depends on that invariant.
 */
public abstract class Track<T> {

    protected final float[] times;
    private final float duration;

    protected Track(float[] times) {
        this.times = times;
        this.duration = times.length == 0 ? 0f : times[times.length - 1];
    }

    public float duration() {
        return duration;
    }

    public boolean isEmpty() {
        return times.length == 0;
    }

    /**
     * Index of the last keyframe at or before {@code time}. {@code Arrays.binarySearch} returns
     * {@code -(insertionPoint) - 1} for a miss, so {@code -index - 2} is the element before the
     * insertion point — clamped at 0 for a time earlier than the first keyframe.
     */
    protected final int indexAt(float time) {
        int index = Arrays.binarySearch(times, time);
        return index >= 0 ? index : Math.max(-index - 2, 0);
    }

    /** Normalized position between keyframe {@code i} and {@code i + 1}; 0 when they share a time. */
    protected final float localT(int i, float time) {
        float span = times[i + 1] - times[i];
        return span <= 0f ? 0f : Math.min(1f, Math.max(0f, (time - times[i]) / span));
    }

    public abstract T sample(float time);
}
