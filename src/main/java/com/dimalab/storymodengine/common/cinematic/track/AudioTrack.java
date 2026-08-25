package com.dimalab.storymodengine.common.cinematic.track;

import com.dimalab.storymodengine.common.cinematic.state.AudioCue;
import net.minecraft.sounds.SoundEvent;
import org.joml.Vector3f;

import java.util.List;
import java.util.Optional;

/**
 * One-shot sound cues, each scheduled to fire on an exact tick — not a continuous state like the
 * other tracks, so {@link #evaluate} must only ever be called once per game tick (never once per
 * render frame like {@code CameraTrack}/{@code ActorTrack}), or the same cue would fire multiple
 * times. {@code CutsceneRuntime} enforces that split. No mixing or fading, and no stoppable/looping
 * sounds (a cutscene's own cues are one-shot stingers/lines by nature — see {@code
 * ARCHITECTURE.md}'s known limitations for why a full {@code SoundInstance} lifecycle isn't built);
 * spatial positioning is supported (see {@link Cue#position()}).
 */
public final class AudioTrack implements Track<Optional<AudioCue>> {

    private final List<Cue> cues;

    public AudioTrack(List<Cue> cues) {
        this.cues = List.copyOf(cues);
    }

    @Override
    public Optional<AudioCue> evaluate(int tick, float partialTick) {
        for (Cue cue : cues) {
            if (cue.tick() == tick) {
                return Optional.of(new AudioCue(cue.sound(), cue.volume(), cue.pitch(), cue.position()));
            }
        }
        return Optional.empty();
    }

    /**
     * One scheduled sound — internal to {@link AudioTrack}, never needed outside it. {@code
     * position} is {@code null} for the original non-spatial behavior; see {@link AudioCue}.
     */
    public record Cue(int tick, SoundEvent sound, float volume, float pitch, Vector3f position) {

        public Cue(int tick, SoundEvent sound, float volume, float pitch) {
            this(tick, sound, volume, pitch, null);
        }
    }
}
