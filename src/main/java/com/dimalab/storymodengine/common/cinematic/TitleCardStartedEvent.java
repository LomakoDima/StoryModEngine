package com.dimalab.storymodengine.common.cinematic;

import com.dimalab.storymodengine.api.event.Event;
import net.minecraft.server.level.ServerPlayer;

/**
 * Posted server-side when a title card starts for {@code player} — no title-card id (unlike {@code
 * CutsceneCompletedEvent}) since a player has at most one active title card at a time (starting a
 * new one cancels the old one first, the same policy {@code CinematicManager} already uses for
 * cutscenes), so nothing needs disambiguating.
 */
public record TitleCardStartedEvent(ServerPlayer player) implements Event {
}
