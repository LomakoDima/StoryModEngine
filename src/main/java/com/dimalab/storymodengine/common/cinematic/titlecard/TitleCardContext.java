package com.dimalab.storymodengine.common.cinematic.titlecard;

import net.minecraft.world.entity.player.Player;

/**
 * The per-play, Minecraft-specific binding layer for a {@link TitleCard} — the same role {@code
 * CutsceneContext} plays for {@code Cutscene}, kept deliberately minimal since a title card needs
 * nothing but "who's watching" today. Exists as its own type (rather than passing a bare {@code
 * Player} around) purely so future presentation options (a background image, a screen position)
 * have a place to live without touching {@link TitleCardInstance}/{@link TitleCardRuntime}.
 */
public record TitleCardContext(Player viewer) {
}
