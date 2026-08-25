package com.dimalab.storymodengine.common.cinematic.titlecard;

import com.dimalab.storymodengine.api.cinematic.PlaybackState;

/**
 * One running play-through of a {@link TitleCard} — the mutable half of "definition ≠ instance",
 * mirroring {@code CutsceneInstance} exactly, down to reusing the same {@link PlaybackState} enum
 * (a title card simply never reaches {@code PAUSED}) rather than a duplicate one. Every mutator is
 * package-private, driven only by {@link TitleCardRuntime}.
 */
public final class TitleCardInstance {

    private final TitleCard definition;
    private final TitleCardContext context;
    private int currentTick;
    private PlaybackState state = PlaybackState.NOT_STARTED;

    public TitleCardInstance(TitleCard definition, TitleCardContext context) {
        this.definition = definition;
        this.context = context;
    }

    public TitleCard definition() {
        return definition;
    }

    public TitleCardContext context() {
        return context;
    }

    public int currentTick() {
        return currentTick;
    }

    public PlaybackState state() {
        return state;
    }

    void start() {
        currentTick = 0;
        state = PlaybackState.PLAYING;
    }

    /** Advances one tick if currently {@code PLAYING} — a no-op otherwise. Moves to {@code COMPLETED} once past the fadeIn+hold+fadeOut total. */
    void advanceTick() {
        if (state != PlaybackState.PLAYING) {
            return;
        }
        currentTick++;
        if (currentTick >= definition.totalTicks()) {
            state = PlaybackState.COMPLETED;
        }
    }

    void cancel() {
        if (state == PlaybackState.PLAYING) {
            state = PlaybackState.CANCELLED;
        }
    }
}
