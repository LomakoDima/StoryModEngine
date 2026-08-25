package com.dimalab.storymodengine.common.cinematic.clip;

import com.dimalab.storymodengine.common.cinematic.track.Track;

/**
 * A time-bounded segment wrapping any {@link Track}{@code <S>} — "this track's content is only
 * active between {@code startTick} and {@code startTick + durationTicks}". The generic primitive
 * a future track type reaches for when it needs several, non-overlapping time-bounded regions on
 * one timeline (see {@code cinematic.track.FadeTrack}, the first concrete user), instead of
 * {@code Timeline} growing a new bespoke list shape per feature. Deliberately not retrofitted onto
 * {@code Shot}/{@code CameraTrack}/{@code ActorTrack} — those already have their own established,
 * working shape and the task calls for extending, not rewriting, the existing architecture.
 *
 * <p>{@link #evaluate} samples {@code content} at time local to the clip (i.e. {@code tick -
 * startTick}), so a clip's own {@code Track} is always authored as if it started at tick 0 —
 * the same convention every existing {@code KeyframeTrack} inside a {@code Shot} already uses
 * relative to its own shot's start.
 */
public record Clip<S>(int startTick, int durationTicks, Track<S> content) {

    public boolean isActiveAt(int tick) {
        return tick >= startTick && tick < startTick + durationTicks;
    }

    public S evaluate(int tick, float partialTick) {
        return content.evaluate(tick - startTick, partialTick);
    }
}
