package com.dimalab.storymodengine.common.cinematic.keyframe;

import com.dimalab.storymodengine.api.math.interp.Interpolator;

/**
 * One point on a {@link KeyframeTrack}: a value fixed at {@code tick}, plus the {@link
 * Interpolator} used to blend from it toward the *next* keyframe (the last keyframe's own
 * interpolator is never consulted, since there's no next segment). Easing attaches the same way
 * it attaches to any {@code Interpolator} — via {@link Interpolator#eased}, no separate field here.
 */
public record Keyframe<T>(int tick, T value, Interpolator<T> interpolator) {
}
