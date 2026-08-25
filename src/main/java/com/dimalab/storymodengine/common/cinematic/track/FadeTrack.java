package com.dimalab.storymodengine.common.cinematic.track;

import com.dimalab.storymodengine.common.cinematic.clip.Clip;
import com.dimalab.storymodengine.common.cinematic.state.FadeState;

import java.util.List;
import java.util.Optional;

/**
 * Zero or more full-screen fades, each a {@link Clip}{@code <FadeState>} — non-overlapping by
 * convention (if two do overlap, the first one found wins, the same "first match" rule {@code
 * Timeline#activeShot} already uses for shots). {@link #evaluate} returns whichever clip covers
 * {@code tick}, or empty when no fade is active — {@code cinematic.client.FadeOverlay} draws
 * nothing in that case.
 */
public final class FadeTrack implements Track<Optional<FadeState>> {

    private final List<Clip<FadeState>> clips;

    public FadeTrack(List<Clip<FadeState>> clips) {
        this.clips = List.copyOf(clips);
    }

    @Override
    public Optional<FadeState> evaluate(int tick, float partialTick) {
        for (Clip<FadeState> clip : clips) {
            if (clip.isActiveAt(tick)) {
                return Optional.of(clip.evaluate(tick, partialTick));
            }
        }
        return Optional.empty();
    }
}
