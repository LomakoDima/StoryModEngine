package com.dimalab.storymodengine.api.math.curve;

import com.dimalab.storymodengine.api.math.interp.Interpolator;

import java.util.List;

/**
 * A Bezier curve of any degree (2 control points = linear, 3 = quadratic, 4 = cubic, and so on),
 * evaluated by de Casteljau's algorithm — repeated {@link Interpolator#interpolate} calls blending
 * adjacent points, one fewer each pass, until a single point remains. De Casteljau never needs
 * anything but pairwise blending, which is exactly why this works unchanged for
 * {@code Curve<Vector3f>}, {@code Curve<Quaternionf>} (each blend a proper slerp, not a lerp of
 * components), {@code Curve<Integer>} (ARGB colors), or any other type with a registered
 * {@link Interpolator}.
 *
 * <p>Every {@link #sample} allocates a small scratch list — cheap for the handful of control
 * points a curve normally has, but not zero-allocation; a hot per-frame path sampling the same
 * curve repeatedly should cache results rather than re-run de Casteljau every frame.
 */
public final class BezierCurve<T> implements Curve<T> {

    private final List<T> controlPoints;
    private final Interpolator<T> interpolator;

    private BezierCurve(List<T> controlPoints, Interpolator<T> interpolator) {
        if (controlPoints.size() < 2) {
            throw new IllegalArgumentException("A Bezier curve needs at least 2 control points, got " + controlPoints.size());
        }
        this.controlPoints = List.copyOf(controlPoints);
        this.interpolator = interpolator;
    }

    public static <T> BezierCurve<T> of(Interpolator<T> interpolator, List<T> controlPoints) {
        return new BezierCurve<>(controlPoints, interpolator);
    }

    @SafeVarargs
    public static <T> BezierCurve<T> of(Interpolator<T> interpolator, T... controlPoints) {
        return of(interpolator, List.of(controlPoints));
    }

    public List<T> controlPoints() {
        return controlPoints;
    }

    @Override
    @SuppressWarnings("unchecked")
    public T sample(float t) {
        Object[] work = controlPoints.toArray();
        for (int remaining = work.length - 1; remaining > 0; remaining--) {
            for (int i = 0; i < remaining; i++) {
                work[i] = interpolator.interpolate((T) work[i], (T) work[i + 1], t);
            }
        }
        return (T) work[0];
    }
}
