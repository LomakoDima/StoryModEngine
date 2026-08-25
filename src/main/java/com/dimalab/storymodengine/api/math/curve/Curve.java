package com.dimalab.storymodengine.api.math.curve;

import java.util.ArrayList;
import java.util.List;

/**
 * A parametric path through values of type {@code T}, sampled over {@code t ∈ [0, 1]}. Every
 * implementation in this package — {@link BezierCurve}, {@link CatmullRomSpline},
 * {@link UniformBSpline} — is built from nothing but repeated calls to a single
 * {@code com.dimalab.storymodengine.api.math.interp.Interpolator<T>}, which is what lets the same
 * curve code drive a camera through {@code Vector3f} waypoints, blend a sequence of
 * {@code Quaternionf} orientations, or animate a color or a {@code Transform}, without a separate
 * curve implementation per type.
 */
@FunctionalInterface
public interface Curve<T> {

    T sample(float t);

    /** {@code steps + 1} evenly-spaced samples across {@code [0, 1]}, inclusive of both ends. */
    default List<T> sample(int steps) {
        List<T> result = new ArrayList<>(steps + 1);
        for (int i = 0; i <= steps; i++) {
            result.add(sample((float) i / steps));
        }
        return result;
    }
}
