package com.dimalab.storymodengine.common.cinematic.state;

/**
 * A full-screen color overlay at one instant. {@code rgbColor} carries no alpha of its own —
 * {@code opacity} ({@code [0, 1]}, {@code 0} meaning fully transparent) is the sole alpha control,
 * kept separate so a fade's color and its opacity ramp can be authored/interpolated independently
 * without the two fighting over the same channel. Plain data, like every other {@code state}
 * record; only {@code cinematic.client.FadeOverlay} ever draws anything from it.
 */
public record FadeState(int rgbColor, float opacity) {
}
