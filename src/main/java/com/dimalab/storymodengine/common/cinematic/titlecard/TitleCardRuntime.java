package com.dimalab.storymodengine.common.cinematic.titlecard;

import com.dimalab.storymodengine.api.cinematic.PlaybackState;
import com.dimalab.storymodengine.common.logging.EngineLog;

/**
 * Drives one {@link TitleCardInstance}'s lifecycle — mirrors {@code CutsceneRuntime}, much smaller
 * (no pause/seek/speed; a title card is a short, linear fade envelope with nothing to scrub).
 * Applying the evaluated {@code TitleCardState} to anything Minecraft-specific is the caller's own
 * job (see {@code cinematic.client.ClientTitleCardPlayer}), keeping this class free of client-only
 * imports exactly like {@code CutsceneRuntime}.
 */
public final class TitleCardRuntime {

    private final TitleCardInstance instance;

    public TitleCardRuntime(TitleCard definition, TitleCardContext context) {
        this.instance = new TitleCardInstance(definition, context);
    }

    public TitleCardInstance instance() {
        return instance;
    }

    public void play() {
        instance.start();
        EngineLog.channel("Cinematic").info("TitleCard '{}' started", instance.definition().title().getString());
    }

    public void cancel() {
        if (instance.state() == PlaybackState.PLAYING) {
            instance.cancel();
            EngineLog.channel("Cinematic").info("TitleCard '{}' cancelled", instance.definition().title().getString());
        }
    }

    public boolean isComplete() {
        PlaybackState state = instance.state();
        return state == PlaybackState.COMPLETED || state == PlaybackState.CANCELLED;
    }

    /** Advances one tick. Logs once, on the transition into {@code COMPLETED}. */
    public void tickOnce() {
        PlaybackState before = instance.state();
        instance.advanceTick();
        if (before != PlaybackState.COMPLETED && instance.state() == PlaybackState.COMPLETED) {
            EngineLog.channel("Cinematic").info("TitleCard '{}' completed", instance.definition().title().getString());
        }
    }
}
