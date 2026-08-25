package com.dimalab.storymodengine.common.cinematic.track;

import com.dimalab.storymodengine.api.event.Event;

import java.util.List;
import java.util.function.Supplier;

/**
 * Zero or more arbitrary engine events fired once each, on the exact tick they're scheduled for —
 * not a {@link Track} (there's no single "current value" to evaluate; posting an event is a side
 * effect). Walked from {@code CinematicManager#tick()}, server-side only — mirroring {@code
 * cinematic.Trigger}'s actual (server-only) firing today, not its Javadoc's broader claim, so this
 * stays consistent with what already ships rather than introducing a second, client-side firing of
 * the identical cue list that nothing here has been exercised against. A cutscene that needs a
 * purely client-local visual reaction can still post through a client-side listener reacting to
 * whatever server-posted event this fires — the point is *one* deterministic fire per cue, not
 * which side happens to observe it.
 */
public final class EventTrack {

    private final List<EventCue> cues;

    public EventTrack(List<EventCue> cues) {
        this.cues = List.copyOf(cues);
    }

    public List<EventCue> cues() {
        return cues;
    }

    /** One scheduled event — fires once, exactly on {@link #tick()}. */
    public record EventCue(int tick, Supplier<Event> event) {
    }
}
