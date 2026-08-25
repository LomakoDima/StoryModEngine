package com.dimalab.storymodengine.client.cinematic;

import com.dimalab.storymodengine.common.cinematic.state.AudioCue;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import org.joml.Vector3f;

/**
 * Plays one {@link AudioCue} locally, once — no mixing or looping (see {@code ARCHITECTURE.md}'s
 * known limitations: a cutscene's cues are one-shot stingers/lines by nature, so a full {@code
 * SoundInstance} lifecycle isn't built). {@link AudioCue#position()} selects between the original
 * non-spatial behavior (played "on" the viewer) and a positioned world sound.
 */
final class AudioApplier {

    private AudioApplier() {
    }

    static void play(AudioCue cue) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        Vector3f position = cue.position();
        if (position == null) {
            minecraft.player.playSound(cue.sound(), cue.volume(), cue.pitch());
        } else if (minecraft.level != null) {
            minecraft.level.playLocalSound(position.x, position.y, position.z,
                    cue.sound(), SoundSource.PLAYERS, cue.volume(), cue.pitch(), false);
        }
    }
}
