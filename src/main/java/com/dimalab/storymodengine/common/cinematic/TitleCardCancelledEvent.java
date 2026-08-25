package com.dimalab.storymodengine.common.cinematic;

import com.dimalab.storymodengine.api.event.Event;
import net.minecraft.server.level.ServerPlayer;

/** Posted server-side when {@code player}'s title card is cancelled early — either explicitly, or replaced by a new one starting. */
public record TitleCardCancelledEvent(ServerPlayer player) implements Event {
}
