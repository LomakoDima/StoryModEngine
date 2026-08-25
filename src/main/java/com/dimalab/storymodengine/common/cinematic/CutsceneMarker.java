package com.dimalab.storymodengine.common.cinematic;

/**
 * A named point on the timeline — {@code CutsceneRuntime#jumpTo(String)} resolves through a
 * definition's marker list to reposition playback, without the caller needing to know the raw
 * tick number. Purely descriptive data, like {@link Trigger}; carries no behavior of its own.
 */
public record CutsceneMarker(String name, int tick) {
}
