package com.dimalab.storymodengine.common.model.track;

import com.dimalab.storymodengine.api.math.interp.Interpolators;
import org.joml.Quaternionf;

/** Rotation keyframes. Blending delegates to {@code api.math.interp.Interpolators.QUATERNION_SLERP} — never a component-wise average of quaternions. */
public final class QuatTrack extends Track<Quaternionf> {

    private final Quaternionf[] values;
    /** Tangents are stored as raw 4-component tuples via {@link Quaternionf} for convenience — they are not themselves rotations (not unit-length), just a 4-float bundle. */
    private final Quaternionf[] inTangents;
    private final Quaternionf[] outTangents;
    private final TrackInterpolation interpolation;

    /** LINEAR/STEP only — every existing caller (the non-cubic import path, and the many synthetic tracks built directly in {@code ModelSelfTest}) keeps using this unchanged. */
    public QuatTrack(float[] times, Quaternionf[] values, boolean step) {
        this(times, values, null, null, step ? TrackInterpolation.STEP : TrackInterpolation.LINEAR);
    }

    /** CUBICSPLINE — {@code inTangents}/{@code outTangents} required (same length as {@code values}, one entry per keyframe); ignored for LINEAR/STEP. */
    public QuatTrack(float[] times, Quaternionf[] values, Quaternionf[] inTangents, Quaternionf[] outTangents, TrackInterpolation interpolation) {
        super(times);
        this.values = values;
        this.inTangents = inTangents;
        this.outTangents = outTangents;
        this.interpolation = interpolation;
    }

    @Override
    public Quaternionf sample(float time) {
        if (values.length == 0) {
            return new Quaternionf();
        }
        if (values.length == 1 || time <= times[0]) {
            return new Quaternionf(values[0]);
        }
        if (time >= times[times.length - 1]) {
            return new Quaternionf(values[values.length - 1]);
        }
        int i = indexAt(time);
        return switch (interpolation) {
            case STEP -> new Quaternionf(values[i]);
            case CUBICSPLINE -> sampleCubicSpline(i, time);
            case LINEAR -> Interpolators.QUATERNION_SLERP.interpolate(values[i], values[i + 1], localT(i, time));
        };
    }

    /**
     * glTF's cubic Hermite spline (spec Appendix C), applied to (x,y,z,w) independently exactly as
     * the spec requires, then normalized — the one difference from {@link Vec3Track}'s own version,
     * needed because a component-wise Hermite blend of two unit quaternions isn't itself unit-length.
     */
    private Quaternionf sampleCubicSpline(int i, float time) {
        float s = localT(i, time);
        float dt = times[i + 1] - times[i];
        float s2 = s * s;
        float s3 = s2 * s;
        float h00 = 2 * s3 - 3 * s2 + 1;
        float h10 = s3 - 2 * s2 + s;
        float h01 = -2 * s3 + 3 * s2;
        float h11 = s3 - s2;
        Quaternionf v0 = values[i];
        Quaternionf v1 = values[i + 1];
        Quaternionf m0 = outTangents[i];
        Quaternionf m1 = inTangents[i + 1];
        return new Quaternionf(
                h00 * v0.x + dt * h10 * m0.x + h01 * v1.x + dt * h11 * m1.x,
                h00 * v0.y + dt * h10 * m0.y + h01 * v1.y + dt * h11 * m1.y,
                h00 * v0.z + dt * h10 * m0.z + h01 * v1.z + dt * h11 * m1.z,
                h00 * v0.w + dt * h10 * m0.w + h01 * v1.w + dt * h11 * m1.w)
                .normalize();
    }
}
