package com.dimalab.storymodengine.api.math.interp;

/**
 * A value that changes once per game tick but needs to render smoothly between ticks — the same
 * previous/current-plus-partial-tick pattern {@code Entity} uses internally for position and
 * rotation ({@code Entity.xo}/{@code x}, sampled via {@code getPosition(partialTick)}), made
 * generic and available for any custom, engine-owned state a mod author's own tickable object
 * carries (a cutscene camera's pose, a custom animated block's progress, ...). Minecraft's own
 * per-field interpolation isn't reusable outside {@code Entity}; this is.
 *
 * <p>Mutable by design — call {@link #tick(Object)} once per game tick to advance, and
 * {@link #get(float)} as often as needed (typically once per render frame) with that frame's
 * partial tick. Not thread-safe; use one instance per owner, on whichever side (client or server)
 * that owner already ticks on.
 */
public final class TickValue<T> {

    private final Interpolator<T> interpolator;
    private T previous;
    private T current;

    private TickValue(Interpolator<T> interpolator, T initial) {
        this.interpolator = interpolator;
        this.previous = initial;
        this.current = initial;
    }

    public static <T> TickValue<T> of(Interpolator<T> interpolator, T initial) {
        return new TickValue<>(interpolator, initial);
    }

    /** Advances one tick: what was "current" becomes "previous", and {@code value} becomes current. */
    public void tick(T value) {
        previous = current;
        current = value;
    }

    /** Snaps both previous and current to {@code value} — use on (re)spawn/teleport to avoid a visible lerp. */
    public void reset(T value) {
        previous = value;
        current = value;
    }

    /** The smoothly-interpolated value for a render frame at this partial tick, {@code [0, 1]}. */
    public T get(float partialTick) {
        return interpolator.interpolate(previous, current, partialTick);
    }

    public T current() {
        return current;
    }

    public T previous() {
        return previous;
    }
}
