package com.dimalab.storymodengine.common.cinematic.state;

import net.minecraft.sounds.SoundEvent;
import org.joml.Vector3f;

/**
 * "Play this sound now" — the payload an {@code AudioTrack} evaluates to on the exact tick it
 * should fire. {@code position} is {@code null} for the original, non-spatial behavior (played
 * "on" the viewing player, via {@code Minecraft.player.playSound}); when set, {@code
 * cinematic.client.AudioApplier} plays it as a positioned world sound instead.
 */
public record AudioCue(SoundEvent sound, float volume, float pitch, Vector3f position) {
}
