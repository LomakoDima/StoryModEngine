package com.dimalab.storymodengine.common.cinematic.keyframe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A single animated channel — one sorted list of {@link Keyframe}s, sampled at a fractional
 * cinematic time ({@code tick + partialTick}). Shared by every concrete track that needs "find the
 * surrounding pair, blend between them" (currently {@code CameraTrack}'s position/rotation/FOV
 * channels and {@code ActorTrack}'s position/rotation/visibility channels) so that logic exists
 * exactly once. Immutable and side-effect-free — {@link #evaluate} is a pure function of time, the
 * same guarantee every {@code cinematic} type upholds (see {@code ARCHITECTURE.md}'s "deterministic
 * evaluation" section).
 */
public final class KeyframeTrack<T> {

    private final List<Keyframe<T>> keyframes;

    private KeyframeTrack(List<Keyframe<T>> keyframes) {
        this.keyframes = keyframes;
    }

    /** Copies and sorts {@code keyframes} by tick — the caller's own list order doesn't matter. */
    public static <T> KeyframeTrack<T> of(List<Keyframe<T>> keyframes) {
        if (keyframes.isEmpty()) {
            throw new IllegalArgumentException("A KeyframeTrack needs at least one keyframe");
        }
        List<Keyframe<T>> sorted = new ArrayList<>(keyframes);
        sorted.sort(Comparator.comparingInt(Keyframe::tick));
        return new KeyframeTrack<>(List.copyOf(sorted));
    }

    /**
     * The blended value at {@code tick + partialTick}. Clamps to the first/last keyframe's value
     * outside the keyframed range, rather than extrapolating.
     */
    public T evaluate(int tick, float partialTick) {
        float time = tick + partialTick;

        Keyframe<T> first = keyframes.get(0);
        if (time <= first.tick()) {
            return first.value();
        }
        Keyframe<T> last = keyframes.get(keyframes.size() - 1);
        if (time >= last.tick()) {
            return last.value();
        }
        for (int i = 0; i < keyframes.size() - 1; i++) {
            Keyframe<T> a = keyframes.get(i);
            Keyframe<T> b = keyframes.get(i + 1);
            if (time >= a.tick() && time < b.tick()) {
                float localT = (time - a.tick()) / (b.tick() - a.tick());
                return a.interpolator().interpolate(a.value(), b.value(), localT);
            }
        }
        return last.value(); // unreachable — the bounds checks above cover the full range
    }
}
