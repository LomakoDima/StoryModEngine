package com.dimalab.storymodengine.common.model.track;

/** How a {@link Track} samples between two keyframes. */
public enum TrackInterpolation {
    LINEAR,
    STEP,
    /** glTF's cubic Hermite spline — needs each keyframe's in/out tangents, not just its value. */
    CUBICSPLINE
}
