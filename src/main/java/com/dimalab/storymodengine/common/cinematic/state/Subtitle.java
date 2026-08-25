package com.dimalab.storymodengine.common.cinematic.state;

/**
 * One subtitle entry — plain data, separate from however it ends up rendered (see {@code
 * cinematic.client.SubtitleOverlay}, the only class that draws anything). {@code speaker} is
 * {@code null} when the line has no attributed speaker. {@code fadeInTicks}/{@code fadeOutTicks}
 * describe an opacity ramp at the start/end of the line's own {@code [startTick, endTick)} window
 * (clamped so they never overlap past the midpoint of a very short line); {@code rgbColor}/{@code
 * position} are opaque style hints a renderer may use however it likes — {@code Subtitle} itself
 * has no notion of fonts, screen coordinates, or GUI widgets.
 */
public record Subtitle(String text, int startTick, int endTick, String speaker,
                        int fadeInTicks, int fadeOutTicks, int rgbColor, Position position) {

    public Subtitle(String text, int startTick, int endTick, String speaker) {
        this(text, startTick, endTick, speaker, 0, 0, 0xFFFFFF, Position.BOTTOM);
    }

    public boolean isActiveAt(int tick) {
        return tick >= startTick && tick < endTick;
    }

    /**
     * The opacity {@code [0, 1]} this line should render at for {@code tick}, ramping in/out over
     * {@link #fadeInTicks()}/{@link #fadeOutTicks()} — {@code 0} outside {@link #isActiveAt}.
     */
    public float opacityAt(int tick) {
        if (!isActiveAt(tick)) {
            return 0f;
        }
        float elapsed = tick - startTick;
        float remaining = endTick - tick;
        float in = fadeInTicks <= 0 ? 1f : Math.min(1f, elapsed / fadeInTicks);
        float out = fadeOutTicks <= 0 ? 1f : Math.min(1f, remaining / fadeOutTicks);
        return Math.min(in, out);
    }

    /** Where a renderer should anchor this line on screen — a style hint, not a coordinate. */
    public enum Position {
        BOTTOM, TOP
    }
}
