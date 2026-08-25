package com.dimalab.storymodengine.common.cinematic;

import com.dimalab.storymodengine.api.event.Event;
import net.minecraft.server.level.ServerPlayer;

/**
 * Posted server-side when {@code player}'s title card reaches the end of its fadeOut naturally —
 * the hook {@code Flow.waitForEvent(TitleCardCompletedEvent.class, ...)} continues on, exactly the
 * way it already does for {@code CutsceneCompletedEvent}.
 */
public record TitleCardCompletedEvent(ServerPlayer player) implements Event {
}
