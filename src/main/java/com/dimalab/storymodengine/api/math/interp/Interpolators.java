package com.dimalab.storymodengine.api.math.interp;

import com.dimalab.storymodengine.api.math.transform.Transform;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * The built-in {@link Interpolator}s the rest of the engine is expected to reach for, one per type
 * that needs to be blended somewhere in {@code content}, {@code resource}, or the curve/transform
 * system. Every one of these delegates to math Minecraft or JOML already implements correctly —
 * none of it is reimplemented here:
 *
 * <ul>
 *   <li>{@link #FLOAT}/{@link #DOUBLE} — {@code Mth.lerp}</li>
 *   <li>{@link #ANGLE_DEGREES} — {@code Mth.rotLerp} (shortest-path, handles the 359°→1° wraparound)</li>
 *   <li>{@link #VECTOR2F}/{@link #VECTOR3F}/{@link #VECTOR4F} — JOML's own {@code lerp}</li>
 *   <li>{@link #QUATERNION_SLERP}/{@link #QUATERNION_NLERP} — JOML's own {@code slerp}/{@code nlerp}</li>
 *   <li>{@link #ARGB_COLOR} — {@code FastColor.ARGB32.lerp}</li>
 *   <li>{@link #TRANSFORM} — {@code Transformation.slerp}, itself built from the two entries above</li>
 * </ul>
 *
 * <p><b>Rotation:</b> {@link #QUATERNION_SLERP} is the correct default — constant angular speed,
 * always the short way round. {@link #QUATERNION_NLERP} is cheaper (no trig) and fine for small
 * angles or once-per-frame camera nudges, but does not move at constant speed over a large angle;
 * reach for it deliberately, not as the default.
 */
public final class Interpolators {

    public static final Interpolator<Float> FLOAT = new Interpolator<>() {
        @Override
        public Float interpolate(Float start, Float end, float t) {
            return Mth.lerp(t, start, end);
        }
    };

    public static final Interpolator<Double> DOUBLE = new Interpolator<>() {
        @Override
        public Double interpolate(Double start, Double end, float t) {
            return Mth.lerp((double) t, start, end);
        }
    };

    /** Degrees, shortest angular path — never interpolate a raw yaw/pitch with {@link #FLOAT}. */
    public static final Interpolator<Float> ANGLE_DEGREES = new Interpolator<>() {
        @Override
        public Float interpolate(Float start, Float end, float t) {
            return Mth.rotLerp(t, start, end);
        }
    };

    public static final Interpolator<Vector2f> VECTOR2F = new Interpolator<>() {
        @Override
        public Vector2f interpolate(Vector2f start, Vector2f end, float t) {
            return start.lerp(end, t, new Vector2f());
        }

        @Override
        public Vector2f interpolate(Vector2f start, Vector2f end, float t, Vector2f dest) {
            return start.lerp(end, t, dest);
        }
    };

    public static final Interpolator<Vector3f> VECTOR3F = new Interpolator<>() {
        @Override
        public Vector3f interpolate(Vector3f start, Vector3f end, float t) {
            return start.lerp(end, t, new Vector3f());
        }

        @Override
        public Vector3f interpolate(Vector3f start, Vector3f end, float t, Vector3f dest) {
            return start.lerp(end, t, dest);
        }
    };

    public static final Interpolator<Vector4f> VECTOR4F = new Interpolator<>() {
        @Override
        public Vector4f interpolate(Vector4f start, Vector4f end, float t) {
            return start.lerp(end, t, new Vector4f());
        }

        @Override
        public Vector4f interpolate(Vector4f start, Vector4f end, float t, Vector4f dest) {
            return start.lerp(end, t, dest);
        }
    };

    /** Spherical linear interpolation — constant angular speed, shortest arc. The default for rotation. */
    public static final Interpolator<Quaternionf> QUATERNION_SLERP = new Interpolator<>() {
        @Override
        public Quaternionf interpolate(Quaternionf start, Quaternionf end, float t) {
            return start.slerp(end, t, new Quaternionf());
        }

        @Override
        public Quaternionf interpolate(Quaternionf start, Quaternionf end, float t, Quaternionf dest) {
            return start.slerp(end, t, dest);
        }
    };

    /** Normalized linear interpolation — cheaper than slerp, not constant-speed. Use deliberately. */
    public static final Interpolator<Quaternionf> QUATERNION_NLERP = new Interpolator<>() {
        @Override
        public Quaternionf interpolate(Quaternionf start, Quaternionf end, float t) {
            return start.nlerp(end, t, new Quaternionf());
        }

        @Override
        public Quaternionf interpolate(Quaternionf start, Quaternionf end, float t, Quaternionf dest) {
            return start.nlerp(end, t, dest);
        }
    };

    /** Packed ARGB {@code int}, channel-wise — same blend Minecraft itself uses for tinting. */
    public static final Interpolator<Integer> ARGB_COLOR = new Interpolator<>() {
        @Override
        public Integer interpolate(Integer start, Integer end, float t) {
            return FastColor.ARGB32.lerp(t, start, end);
        }
    };

    /**
     * Translation lerped, rotation slerped, scale lerped — delegates to
     * {@code com.mojang.math.Transformation#slerp} via {@link Transform#toMojang()}. Correct for
     * the same reason {@link #QUATERNION_SLERP} is: a transform's rotation component is never
     * blended by component-averaging its quaternion.
     */
    public static final Interpolator<Transform> TRANSFORM = new Interpolator<>() {
        @Override
        public Transform interpolate(Transform start, Transform end, float t) {
            return Transform.from(start.toMojang().slerp(end.toMojang(), t));
        }
    };

    /**
     * Holds {@code start} until {@code t} reaches {@code 1}, then snaps to {@code end} — for values
     * that genuinely shouldn't blend (booleans, enums, discrete states). Generic and reusable on
     * purpose, not tied to any one caller: the same "step" shape any discrete-valued keyframe needs,
     * wherever one turns up.
     */
    public static <T> Interpolator<T> step() {
        return (start, end, t) -> t >= 1f ? end : start;
    }

    private Interpolators() {
    }
}
