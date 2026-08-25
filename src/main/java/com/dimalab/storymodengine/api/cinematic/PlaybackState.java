package com.dimalab.storymodengine.api.cinematic;

/**
 * A {@link CutsceneInstance}'s own lifecycle — independent of anything a {@link Track} evaluates
 * (mirrors {@code FlowRunState}'s relationship to {@code NodeState} in {@code flow} for the same
 * reason: "is this thing currently advancing" is a different question from "what does it currently
 * look like"). {@code SKIPPED} is deliberately not a state yet — see {@code ARCHITECTURE.md}'s
 * known limitations.
 */
public enum PlaybackState {
    NOT_STARTED,
    PLAYING,
    PAUSED,
    COMPLETED,
    CANCELLED
}
