package com.dimalab.storymodengine.common.cinematic.example;

import com.dimalab.storymodengine.api.event.Event;
import net.minecraft.server.level.ServerPlayer;

/**
 * The custom event {@link CinematicShowcaseCommand}'s {@code EventTrack} cue posts mid-playback —
 * exists purely to prove a cutscene can fire an arbitrary, mod-defined {@code Event} through the
 * existing {@code EventBus} (see {@link CinematicShowcaseCommand#onShowcaseSignal}), not something
 * {@code cinematic} itself needs to know about.
 */
public record ShowcaseSignalEvent(ServerPlayer player) implements Event {
}
