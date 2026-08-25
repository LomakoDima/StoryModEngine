package com.dimalab.storymodengine.common.cinematic.track;

/**
 * One independent timeline channel — the shared contract every concrete track ({@code
 * CameraTrack}, {@code ActorTrack}, {@code SubtitleTrack}, {@code AudioTrack}) implements.
 * {@link #evaluate} must be a pure function of {@code (tick, partialTick)}: no hidden mutable
 * state, no side effects, the same input always producing the same output — this is what makes
 * scrubbing, replay, and deterministic multiplayer playback possible later without any track
 * needing to change (see {@code ARCHITECTURE.md}). Applying the evaluated state to anything
 * Minecraft-specific happens entirely outside this interface, in {@code cinematic.client}.
 */
@FunctionalInterface
public interface Track<S> {

    S evaluate(int tick, float partialTick);
}
