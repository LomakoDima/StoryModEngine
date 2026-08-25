package com.dimalab.storymodengine.api.math.curve;

import org.joml.Vector3f;

/**
 * Lets a {@code Curve<Vector3f>} be walked at constant speed rather than constant parametric
 * {@code t} — the difference that matters for a camera path or a moving object, where an evenly
 * spaced-out set of control points on a {@link BezierCurve} or {@link CatmullRomSpline} still
 * doesn't sample at an even <em>distance</em> per {@code t} (curves speed up through tight turns
 * and slow down through gentle ones). Built once per curve by sampling it densely and accumulating
 * straight-line segment lengths — an approximation whose error shrinks as {@code samples} grows,
 * not an exact analytic arc length (which most of these curves don't have a closed form for
 * anyway).
 *
 * <p>Specific to {@code Vector3f} rather than generic over {@code T}: "distance travelled" is a
 * property of positions in space, not of an arbitrary interpolated type (there's no meaningful
 * arc length for a curve of colors or rotations) — see
 * {@code com.dimalab.storymodengine.api.math.curve.CatmullRomSpline.DistanceMetric} for the more
 * general notion this specializes.
 */
public final class ArcLengthTable {

    private final float[] cumulativeLength;
    private final float[] parameter;
    private final float totalLength;

    private ArcLengthTable(float[] cumulativeLength, float[] parameter) {
        this.cumulativeLength = cumulativeLength;
        this.parameter = parameter;
        this.totalLength = cumulativeLength[cumulativeLength.length - 1];
    }

    public static ArcLengthTable build(Curve<Vector3f> curve, int samples) {
        if (samples < 2) {
            throw new IllegalArgumentException("An arc-length table needs at least 2 samples, got " + samples);
        }
        float[] length = new float[samples];
        float[] param = new float[samples];
        Vector3f previous = curve.sample(0f);
        length[0] = 0f;
        param[0] = 0f;
        for (int i = 1; i < samples; i++) {
            float t = (float) i / (samples - 1);
            Vector3f current = curve.sample(t);
            length[i] = length[i - 1] + previous.distance(current);
            param[i] = t;
            previous = current;
        }
        return new ArcLengthTable(length, param);
    }

    public float totalLength() {
        return totalLength;
    }

    /** The curve's {@code t} at a given distance travelled from the start, clamped to the curve's extent. */
    public float parameterAtDistance(float distance) {
        float d = Math.min(Math.max(distance, 0f), totalLength);
        int index = java.util.Arrays.binarySearch(cumulativeLength, d);
        if (index >= 0) {
            return parameter[index];
        }
        int insertion = -index - 1;
        if (insertion <= 0) {
            return parameter[0];
        }
        if (insertion >= cumulativeLength.length) {
            return parameter[parameter.length - 1];
        }
        float lo = cumulativeLength[insertion - 1];
        float hi = cumulativeLength[insertion];
        float segmentT = hi > lo ? (d - lo) / (hi - lo) : 0f;
        return net.minecraft.util.Mth.lerp(segmentT, parameter[insertion - 1], parameter[insertion]);
    }

    /** {@code t} for the point that is {@code fraction ∈ [0, 1]} of the way along the curve by distance. */
    public float parameterAtFraction(float fraction) {
        return parameterAtDistance(fraction * totalLength);
    }
}
