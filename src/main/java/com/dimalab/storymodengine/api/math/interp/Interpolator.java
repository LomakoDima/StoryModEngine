package com.dimalab.storymodengine.api.math.interp;

/**
 * Blends two values of the same type {@code T} at parameter {@code t} — the one primitive the
 * rest of {@code math} is built from. Every curve in {@code math.curve} (Bezier via de Casteljau,
 * Catmull-Rom via the Barry-Goldman construction, a B-spline via the de Boor recurrence) reduces
 * to nothing but repeated calls to this single method, which is what lets one curve implementation
 * work identically over floats, {@code Vector3f} positions, {@code Quaternionf} rotations, ARGB
 * colors, or a full {@code Transform} — the interpolator supplied for {@code T} decides what
 * "blend" means for that type, and the curve algorithm never needs to know.
 *
 * <p>For rotations, this is exactly why passing {@link Interpolators#QUATERNION_SLERP} to a curve
 * or {@link TickValue} is correct where naively lerping Euler angles isn't: every nested blend
 * along the way is a proper spherical interpolation, not a component-wise average.
 *
 * <p>{@code t} is not required to stay within {@code [0, 1]} — callers that need extrapolation
 * (e.g. overshoot easing) may pass values outside that range; whether the result is meaningful
 * depends on the concrete interpolator.
 */
@FunctionalInterface
public interface Interpolator<T> {

    T interpolate(T start, T end, float t);

    /**
     * Same contract as {@link #interpolate(Object, Object, float)}, but writes into {@code dest}
     * instead of allocating a new instance where the implementation can support it — the escape
     * hatch for hot paths (per-frame camera updates, particle ticking) that can't afford a fresh
     * {@code Vector3f}/{@code Quaternionf} every sample. The default just delegates and ignores
     * {@code dest}; built-ins in {@link Interpolators} that wrap a JOML type override it to use
     * that type's own {@code dest}-parameter overload, allocation-free.
     */
    default T interpolate(T start, T end, float t, T dest) {
        return interpolate(start, end, t);
    }

    /**
     * Returns an interpolator that reshapes {@code t} through {@code easing} before blending —
     * the standard way an {@link Easing} curve attaches to any {@code Interpolator<T>} regardless
     * of what {@code T} is.
     */
    default Interpolator<T> eased(Easing easing) {
        Interpolator<T> self = this;
        return new Interpolator<T>() {
            @Override
            public T interpolate(T start, T end, float t) {
                return self.interpolate(start, end, easing.apply(t));
            }

            @Override
            public T interpolate(T start, T end, float t, T dest) {
                return self.interpolate(start, end, easing.apply(t), dest);
            }
        };
    }
}
