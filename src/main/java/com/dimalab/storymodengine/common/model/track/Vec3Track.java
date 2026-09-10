package com.dimalab.storymodengine.common.model.track;

import com.dimalab.storymodengine.api.math.interp.Interpolators;
import org.joml.Vector3f;

/** Translation/scale keyframes. Blending delegates to {@code api.math.interp.Interpolators.VECTOR3F} — this engine's own interpolation primitive, not a hand-rolled lerp. */
public final class Vec3Track extends Track<Vector3f> {

    private final Vector3f[] values;
    private final Vector3f[] inTangents;
    private final Vector3f[] outTangents;
    private final TrackInterpolation interpolation;

    /** LINEAR/STEP only — every existing caller (the non-cubic import path, and the many synthetic tracks built directly in {@code ModelSelfTest}) keeps using this unchanged. */
    public Vec3Track(float[] times, Vector3f[] values, boolean step) {
        this(times, values, null, null, step ? TrackInterpolation.STEP : TrackInterpolation.LINEAR);
    }

    /** CUBICSPLINE — {@code inTangents}/{@code outTangents} required (same length as {@code values}, one entry per keyframe); ignored for LINEAR/STEP. */
    public Vec3Track(float[] times, Vector3f[] values, Vector3f[] inTangents, Vector3f[] outTangents, TrackInterpolation interpolation) {
        super(times);
        this.values = values;
        this.inTangents = inTangents;
        this.outTangents = outTangents;
        this.interpolation = interpolation;
    }

    @Override
    public Vector3f sample(float time) {
        if (values.length == 0) {
            return new Vector3f();
        }
        if (values.length == 1 || time <= times[0]) {
            return new Vector3f(values[0]);
        }
        if (time >= times[times.length - 1]) {
            return new Vector3f(values[values.length - 1]);
        }
        int i = indexAt(time);
        return switch (interpolation) {
            case STEP -> new Vector3f(values[i]);
            case CUBICSPLINE -> sampleCubicSpline(i, time);
            case LINEAR -> Interpolators.VECTOR3F.interpolate(values[i], values[i + 1], localT(i, time));
        };
    }

    /**
     * glTF's cubic Hermite spline (spec Appendix C: Interpolation), applied component-wise — no
     * normalization, unlike {@link QuatTrack}'s own version, since a translation/scale delta has no
     * unit-length constraint to preserve.
     */
    private Vector3f sampleCubicSpline(int i, float time) {
        float s = localT(i, time);
        float dt = times[i + 1] - times[i];
        float s2 = s * s;
        float s3 = s2 * s;
        float h00 = 2 * s3 - 3 * s2 + 1;
        float h10 = s3 - 2 * s2 + s;
        float h01 = -2 * s3 + 3 * s2;
        float h11 = s3 - s2;
        Vector3f v0 = values[i];
        Vector3f v1 = values[i + 1];
        Vector3f m0 = outTangents[i];
        Vector3f m1 = inTangents[i + 1];
        return new Vector3f(
                h00 * v0.x + dt * h10 * m0.x + h01 * v1.x + dt * h11 * m1.x,
                h00 * v0.y + dt * h10 * m0.y + h01 * v1.y + dt * h11 * m1.y,
                h00 * v0.z + dt * h10 * m0.z + h01 * v1.z + dt * h11 * m1.z);
    }
}
