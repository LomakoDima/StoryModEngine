package com.dimalab.storymodengine.common.cinematic.state;

import com.dimalab.storymodengine.common.cinematic.titlecard.TitleCardPhase;
import net.minecraft.network.chat.Component;

import java.util.Optional;

/**
 * The pose a {@code TitleCard} evaluates to at one instant — plain data, exactly like {@link
 * CameraState}/{@link FadeState}: no rendering here, only {@code cinematic.client
 * .TitleCardOverlay} draws anything. {@code opacity} ({@code [0, 1]}) is the single fade value
 * driving both the black background and the text, kept consistent per the task's own requirement.
 */
public record TitleCardState(TitleCardPhase phase, float opacity, Component title, Optional<Component> subtitle) {
}
