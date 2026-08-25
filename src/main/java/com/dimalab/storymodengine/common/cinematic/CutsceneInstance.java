package com.dimalab.storymodengine.common.cinematic;

import com.dimalab.storymodengine.api.cinematic.PlaybackState;
import net.minecraft.util.Mth;

/**
 * One running play-through of a {@link CutsceneDefinition} — the mutable half of "definition ≠
 * instance" (same split {@code Flow}/{@code FlowInstance} already established). Holds nothing but
 * {@link #currentTick()}, {@link #state()}, and {@link #speed()}; every mutator is package-private,
 * driven only by {@link CutsceneRuntime}. {@code currentTick} is a plain cinematic tick count,
 * never wall-clock time — see {@link CutsceneDefinition}'s Javadoc.
 *
 * <p>{@link #seek(int)} repositions playback without replaying anything — it can do this precisely
 * *because* every {@code Track#evaluate(tick, partialTick)} in this system is a pure function of
 * time (see {@code Track}'s Javadoc): jumping {@code currentTick} and letting the next normal apply
 * pass run produces the correct instantaneous frame for free. It deliberately does not scan or fire
 * any one-shot cue between the old and new tick — that is what keeps scrubbing from spamming
 * events/audio (see {@code ARCHITECTURE.md}).
 */
public final class CutsceneInstance {

    private final CutsceneDefinition definition;
    private final CutsceneContext context;
    private float cinematicTime;
    private int currentTick;
    private float speed = 1f;
    private PlaybackState state = PlaybackState.NOT_STARTED;

    public CutsceneInstance(CutsceneDefinition definition, CutsceneContext context) {
        this.definition = definition;
        this.context = context;
    }

    public CutsceneDefinition definition() {
        return definition;
    }

    public CutsceneContext context() {
        return context;
    }

    public int currentTick() {
        return currentTick;
    }

    public PlaybackState state() {
        return state;
    }

    /** Playback speed multiplier — {@code 1.0} is normal speed. Never affects the definition, only this instance. */
    public float speed() {
        return speed;
    }

    void start() {
        cinematicTime = 0f;
        currentTick = 0;
        state = PlaybackState.PLAYING;
    }

    /** Advances by {@link #speed()} ticks if currently {@code PLAYING} — a no-op otherwise. Moves to {@code COMPLETED} at the timeline's duration. */
    void advanceTick() {
        if (state != PlaybackState.PLAYING) {
            return;
        }
        int duration = definition.timeline().durationTicks();
        cinematicTime += speed;
        currentTick = Math.min((int) cinematicTime, duration);
        if (currentTick >= duration) {
            state = PlaybackState.COMPLETED;
        }
    }

    void pause() {
        if (state == PlaybackState.PLAYING) {
            state = PlaybackState.PAUSED;
        }
    }

    void resume() {
        if (state == PlaybackState.PAUSED) {
            state = PlaybackState.PLAYING;
        }
    }

    void cancel() {
        if (state == PlaybackState.PLAYING || state == PlaybackState.PAUSED) {
            state = PlaybackState.CANCELLED;
        }
    }

    void setSpeed(float speed) {
        this.speed = Math.max(0.01f, speed);
    }

    /**
     * Repositions playback to {@code tick}, clamped to {@code [0, durationTicks - 1]}. A no-op
     * unless currently {@code PLAYING} or {@code PAUSED} — see the class Javadoc for why nothing
     * between the old and new tick gets replayed.
     */
    void seek(int tick) {
        if (state != PlaybackState.PLAYING && state != PlaybackState.PAUSED) {
            return;
        }
        int lastTick = Math.max(0, definition.timeline().durationTicks() - 1);
        currentTick = Mth.clamp(tick, 0, lastTick);
        cinematicTime = currentTick;
    }
}
