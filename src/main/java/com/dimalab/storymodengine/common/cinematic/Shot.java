package com.dimalab.storymodengine.common.cinematic;

import com.dimalab.storymodengine.common.cinematic.track.CameraTrack;

/**
 * One bounded camera segment — "start, duration, camera" exactly as specified, deliberately flat
 * (no nested shot composition). A {@link Timeline} holds an ordered list of these; whichever
 * {@code Shot} contains the current tick is the one "in control" of the camera. This is the
 * abstraction future multi-shot cinematics build on, without needing anything richer yet.
 *
 * <p>{@link #transition()}/{@link #transitionTicks()} describe how *this* shot begins relative to
 * whichever shot was active immediately before it — {@link Transition#CUT} (the default, and the
 * only behavior that existed before this field was added, so every existing {@code shot(...)} call
 * is unaffected) is an instant switch; {@link Transition#CROSSFADE}/{@link Transition#FADE} are
 * applied by {@code cinematic.client.ClientCutscenePlayer}, never here — a {@code Shot} only
 * describes *what* transition it wants, not how one is rendered.
 */
public record Shot(int startTick, int durationTicks, CameraTrack camera, Transition transition, int transitionTicks) {

    public boolean isActiveAt(int tick) {
        return tick >= startTick && tick < startTick + durationTicks;
    }

    /** How this shot begins — see the class Javadoc. */
    public enum Transition {
        /** Instant switch — the only behavior this system had before transitions existed. */
        CUT,
        /** Blends the outgoing shot's last camera pose into this shot's first pose over {@link Shot#transitionTicks()}. */
        CROSSFADE,
        /** A brief dip through a full-screen fade, via the same {@code FadeOverlay} a {@code fade(...)} clip uses. */
        FADE
    }
}
