package com.dimalab.storymodengine.common.cinematic;

import com.dimalab.storymodengine.api.cinematic.SkipPolicy;
import com.dimalab.storymodengine.api.cinematic.PlaybackState;
import com.dimalab.storymodengine.common.logging.EngineLog;

/**
 * Drives one {@link CutsceneInstance}'s lifecycle — {@link #tickOnce()} is meant to be called once
 * per game tick (never per render frame); reading the current evaluated state for a frame is the
 * caller's own job (iterate {@code instance().definition().timeline()}'s tracks directly — see
 * {@code cinematic.client.ClientCutscenePlayer}, the only place that actually applies anything to
 * Minecraft). Keeping that application logic out of this class is what keeps {@code cinematic}'s
 * core free of client-only imports (§7/§25 of the task this was built from).
 *
 * <p>{@link #pause()}/{@link #resume()}/{@link #seek(int)}/{@link #setSpeed(float)}/{@link #skip()}
 * only affect *this* (client-side) instance's own tick counter — see {@code ARCHITECTURE.md}'s
 * known limitations for why that's not yet coordinated with the server's independent completion
 * timer.
 */
public final class CutsceneRuntime {

    private final CutsceneInstance instance;

    public CutsceneRuntime(CutsceneDefinition definition, CutsceneContext context) {
        this.instance = new CutsceneInstance(definition, context);
    }

    public CutsceneInstance instance() {
        return instance;
    }

    public void play() {
        instance.start();
        EngineLog.channel("Cinematic").info("Cutscene '{}' started", instance.definition().id());
    }

    public void pause() {
        instance.pause();
    }

    public void resume() {
        instance.resume();
    }

    public void stop() {
        if (instance.state() == PlaybackState.PLAYING || instance.state() == PlaybackState.PAUSED) {
            instance.cancel();
            EngineLog.channel("Cinematic").info("Cutscene '{}' cancelled", instance.definition().id());
        }
    }

    public boolean isPlaying() {
        return instance.state() == PlaybackState.PLAYING;
    }

    public PlaybackState state() {
        return instance.state();
    }

    public void setSpeed(float speed) {
        instance.setSpeed(speed);
    }

    /** Jumps playback to {@code tick} — see {@link CutsceneInstance#seek(int)} for what this does and doesn't replay. */
    public void seek(int tick) {
        instance.seek(tick);
    }

    /** Jumps to a named {@code CutsceneMarker}; logs and does nothing if {@code markerName} doesn't exist on this timeline. */
    public void jumpTo(String markerName) {
        Integer tick = instance.definition().timeline().markerTick(markerName);
        if (tick == null) {
            EngineLog.channel("Cinematic").warn(
                    "jumpTo('{}'): no such marker on cutscene '{}'", markerName, instance.definition().id());
            return;
        }
        instance.seek(tick);
    }

    /** Seeks back to the start — does not change {@link PlaybackState} (a paused restart stays paused). */
    public void restart() {
        instance.seek(0);
    }

    /**
     * Honors this cutscene's {@link SkipPolicy}: jumps to the last tick (or the configured
     * default-skip marker for {@link SkipPolicy#SKIP_TO_MARKER}), letting the very next normal
     * tick complete it exactly like reaching the end naturally would. A no-op, logged at debug,
     * for {@link SkipPolicy#NON_SKIPPABLE}.
     */
    public void skip() {
        Timeline timeline = instance.definition().timeline();
        SkipPolicy policy = timeline.skipPolicy();
        switch (policy) {
            case NON_SKIPPABLE -> EngineLog.channel("Cinematic").debug(
                    "skip(): cutscene '{}' is NON_SKIPPABLE, ignoring", instance.definition().id());
            case SKIP_TO_MARKER -> {
                String marker = timeline.defaultSkipMarker();
                if (marker != null && timeline.markerTick(marker) != null) {
                    jumpTo(marker);
                } else {
                    instance.seek(timeline.durationTicks() - 1);
                }
            }
            default -> instance.seek(timeline.durationTicks() - 1);
        }
    }

    /** Advances one tick. Logs once, on the transition into {@code COMPLETED}. */
    public void tickOnce() {
        PlaybackState before = instance.state();
        instance.advanceTick();
        if (before != PlaybackState.COMPLETED && instance.state() == PlaybackState.COMPLETED) {
            EngineLog.channel("Cinematic").info("Cutscene '{}' completed", instance.definition().id());
        }
    }
}
