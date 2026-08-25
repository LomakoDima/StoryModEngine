package com.dimalab.storymodengine.api.math.curve;

import com.dimalab.storymodengine.api.math.interp.Interpolator;

import java.util.List;

/**
 * A piecewise cubic spline that passes through every control point (unlike {@link BezierCurve},
 * where only the first and last points lie on the curve) — the standard choice for a camera path
 * or an animation track defined by a handful of keyframes the mod author actually wants the object
 * to pass through.
 *
 * <p>Built via the Barry-Goldman construction, which — like de Casteljau for {@link BezierCurve} —
 * needs nothing but nested calls to {@link Interpolator#interpolate}: six blends per sample, all
 * pairwise. That's what keeps this generic over the same {@code T} as everything else in this
 * package, correct for rotations (each nested blend of a {@code Curve<Quaternionf>} is a slerp),
 * and free of any assumption that {@code T} supports addition or scalar multiplication.
 *
 * <p>The curve needs a "phantom" point before the first and after the last control point to
 * compute the tangent at each end; this implementation duplicates the nearest real endpoint for
 * both (the common, simplest boundary behavior), rather than requiring {@code T} to support
 * extrapolation — the tangent at the very ends is consequently a little flatter than a spline with
 * true extrapolated phantoms, which is an acceptable, well-known trade-off for staying generic.
 *
 * <p>Use {@link #uniform} when a {@link DistanceMetric} for {@code T} doesn't exist or doesn't
 * matter; use {@link #centripetal} (the generally-recommended default when it does) to avoid the
 * loops/cusps uniform parametrization can produce when control points are unevenly spaced —
 * needed for {@code Vector3f} waypoints placed by hand, for instance.
 */
public final class CatmullRomSpline<T> implements Curve<T> {

    private static final float MIN_KNOT_SPAN = 1e-4f;

    private final List<T> points;
    private final Interpolator<T> interpolator;
    private final float alpha;
    private final DistanceMetric<T> distanceMetric;

    private CatmullRomSpline(List<T> points, Interpolator<T> interpolator, float alpha, DistanceMetric<T> distanceMetric) {
        if (points.size() < 2) {
            throw new IllegalArgumentException("A Catmull-Rom spline needs at least 2 control points, got " + points.size());
        }
        this.points = List.copyOf(points);
        this.interpolator = interpolator;
        this.alpha = alpha;
        this.distanceMetric = distanceMetric;
    }

    /** Uniform parametrization: every segment treated as equal-length regardless of actual spacing. */
    public static <T> CatmullRomSpline<T> uniform(Interpolator<T> interpolator, List<T> points) {
        return new CatmullRomSpline<>(points, interpolator, 0f, null);
    }

    @SafeVarargs
    public static <T> CatmullRomSpline<T> uniform(Interpolator<T> interpolator, T... points) {
        return uniform(interpolator, List.of(points));
    }

    /** Centripetal parametrization ({@code alpha = 0.5}) — avoids cusps/self-intersections uniform can produce. */
    public static <T> CatmullRomSpline<T> centripetal(Interpolator<T> interpolator, DistanceMetric<T> distanceMetric, List<T> points) {
        return new CatmullRomSpline<>(points, interpolator, 0.5f, distanceMetric);
    }

    /** Chordal parametrization ({@code alpha = 1}) — knot spacing proportional to actual distance. */
    public static <T> CatmullRomSpline<T> chordal(Interpolator<T> interpolator, DistanceMetric<T> distanceMetric, List<T> points) {
        return new CatmullRomSpline<>(points, interpolator, 1f, distanceMetric);
    }

    public List<T> points() {
        return points;
    }

    private int segmentCount() {
        return points.size() - 1;
    }

    @Override
    public T sample(float t) {
        int segments = segmentCount();
        float scaled = Math.min(Math.max(t, 0f), 1f) * segments;
        int segment = Math.min((int) scaled, segments - 1);
        float u = scaled - segment;
        return sampleSegment(segment, u);
    }

    private T sampleSegment(int segment, float u) {
        T p0 = points.get(Math.max(segment - 1, 0));
        T p1 = points.get(segment);
        T p2 = points.get(segment + 1);
        T p3 = points.get(Math.min(segment + 2, points.size() - 1));

        float t0 = 0f;
        float t1 = t0 + knotSpan(p0, p1);
        float t2 = t1 + knotSpan(p1, p2);
        float t3 = t2 + knotSpan(p2, p3);
        float t = t1 + u * (t2 - t1);

        T a1 = interpolator.interpolate(p0, p1, (t - t0) / (t1 - t0));
        T a2 = interpolator.interpolate(p1, p2, (t - t1) / (t2 - t1));
        T a3 = interpolator.interpolate(p2, p3, (t - t2) / (t3 - t2));
        T b1 = interpolator.interpolate(a1, a2, (t - t0) / (t2 - t0));
        T b2 = interpolator.interpolate(a2, a3, (t - t1) / (t3 - t1));
        return interpolator.interpolate(b1, b2, (t - t1) / (t2 - t1));
    }

    private float knotSpan(T a, T b) {
        if (distanceMetric == null) {
            return 1f;
        }
        float distance = distanceMetric.distance(a, b);
        return Math.max((float) Math.pow(distance, alpha), MIN_KNOT_SPAN);
    }

    /** How far apart two points of {@code T} are — needed only for {@link #centripetal}/{@link #chordal}. */
    @FunctionalInterface
    public interface DistanceMetric<T> {
        float distance(T a, T b);
    }
}
