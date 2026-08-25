package com.dimalab.storymodengine.common.cinematic.track;

import com.dimalab.storymodengine.common.cinematic.state.Subtitle;

import java.util.List;
import java.util.Optional;

/** Whichever {@link Subtitle} is active at a given tick, or none — a simple linear scan, fine for the small entry counts a cutscene has. */
public final class SubtitleTrack implements Track<Optional<Subtitle>> {

    private final List<Subtitle> subtitles;

    public SubtitleTrack(List<Subtitle> subtitles) {
        this.subtitles = List.copyOf(subtitles);
    }

    @Override
    public Optional<Subtitle> evaluate(int tick, float partialTick) {
        for (Subtitle subtitle : subtitles) {
            if (subtitle.isActiveAt(tick)) {
                return Optional.of(subtitle);
            }
        }
        return Optional.empty();
    }
}
