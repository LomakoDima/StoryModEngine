package com.dimalab.storymodengine.api.math;

/**
 * The handful of numeric helpers actually missing from {@code Mth} — deliberately small.
 * {@code Mth} already covers {@code clamp}, {@code lerp}, {@code inverseLerp}, {@code map}/
 * {@code clampedMap}, {@code smoothstep}, and more; reach for {@code Mth} directly rather than
 * expecting a duplicate here. This class exists so those few genuine gaps have a home instead of
 * accumulating into an unrelated grab-bag next to the interpolation/curve/transform code.
 */
public final class Numbers {

    private Numbers() {
    }

    /** Clamps to {@code [0, 1]} — the common case of {@code Mth.clamp(v, 0, 1)}, named for what it's for. */
    public static float saturate(float v) {
        return v < 0f ? 0f : Math.min(v, 1f);
    }

    /**
     * Ken Perlin's improved smoothstep: zero first <em>and</em> second derivative at both ends
     * (plain {@code Mth.smoothstep} only zeroes the first) — worth reaching for when a curve or
     * camera move needs to visibly settle rather than merely slow down.
     */
    public static float smootherstep(float t) {
        float x = saturate(t);
        return x * x * x * (x * (x * 6f - 15f) + 10f);
    }

    /** Equality within {@code epsilon} — {@code Mth.equal} exists but hardcodes its tolerance. */
    public static boolean nearlyEqual(float a, float b, float epsilon) {
        return Math.abs(a - b) <= epsilon;
    }
}
