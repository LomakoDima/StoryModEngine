package com.dimalab.storymodengine.common.cinematic;

import com.dimalab.storymodengine.api.cinematic.SkipPolicy;
import com.dimalab.storymodengine.common.cinematic.track.ActionTrack;
import com.dimalab.storymodengine.common.cinematic.track.ActorTrack;
import com.dimalab.storymodengine.common.cinematic.track.AudioTrack;
import com.dimalab.storymodengine.common.cinematic.track.EventTrack;
import com.dimalab.storymodengine.common.cinematic.track.FadeTrack;
import com.dimalab.storymodengine.common.cinematic.track.SubtitleTrack;

import java.util.List;

/**
 * The immutable content of one {@link CutsceneDefinition} — duration plus every track, all
 * side-effect-free (see {@code Track}'s Javadoc for the determinism contract every track upholds).
 * Built only by {@code CutsceneDefinition.Builder}; nothing here is ever constructed directly by a
 * mod author.
 */
public final class Timeline {

    private final int durationTicks;
    private final List<Shot> shots;
    private final List<ActorTrack> actorTracks;
    private final SubtitleTrack subtitles;
    private final AudioTrack audio;
    private final FadeTrack fades;
    private final List<Trigger> triggers;
    private final EventTrack events;
    private final ActionTrack actions;
    private final List<CutsceneMarker> markers;
    private final SkipPolicy skipPolicy;
    private final String defaultSkipMarker;

    Timeline(int durationTicks, List<Shot> shots, List<ActorTrack> actorTracks,
             SubtitleTrack subtitles, AudioTrack audio, FadeTrack fades, List<Trigger> triggers,
             EventTrack events, ActionTrack actions, List<CutsceneMarker> markers,
             SkipPolicy skipPolicy, String defaultSkipMarker) {
        this.durationTicks = durationTicks;
        this.shots = List.copyOf(shots);
        this.actorTracks = List.copyOf(actorTracks);
        this.subtitles = subtitles;
        this.audio = audio;
        this.fades = fades;
        this.triggers = List.copyOf(triggers);
        this.events = events;
        this.actions = actions;
        this.markers = List.copyOf(markers);
        this.skipPolicy = skipPolicy;
        this.defaultSkipMarker = defaultSkipMarker;
    }

    public int durationTicks() {
        return durationTicks;
    }

    public List<Shot> shots() {
        return shots;
    }

    public List<ActorTrack> actorTracks() {
        return actorTracks;
    }

    public SubtitleTrack subtitles() {
        return subtitles;
    }

    public AudioTrack audio() {
        return audio;
    }

    public FadeTrack fades() {
        return fades;
    }

    public List<Trigger> triggers() {
        return triggers;
    }

    public EventTrack events() {
        return events;
    }

    public ActionTrack actions() {
        return actions;
    }

    public List<CutsceneMarker> markers() {
        return markers;
    }

    public SkipPolicy skipPolicy() {
        return skipPolicy;
    }

    /** The tick a {@link SkipPolicy#SKIP_TO_MARKER} definition jumps to, or {@code null} if none was configured. */
    public String defaultSkipMarker() {
        return defaultSkipMarker;
    }

    /** The {@link Shot} in control of the camera at {@code tick}, or {@code null} if none covers it. */
    public Shot activeShot(int tick) {
        for (Shot shot : shots) {
            if (shot.isActiveAt(tick)) {
                return shot;
            }
        }
        return null;
    }

    /** The tick {@code name} refers to, or {@code null} if no such marker exists on this timeline. */
    public Integer markerTick(String name) {
        for (CutsceneMarker marker : markers) {
            if (marker.name().equals(name)) {
                return marker.tick();
            }
        }
        return null;
    }
}
