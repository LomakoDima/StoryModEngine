package com.dimalab.storymodengine.common.event.example;

import com.dimalab.storymodengine.api.event.Event;
import net.minecraft.server.level.ServerPlayer;

/** Posted by {@code /storymodengine eventtest instance} — only {@link InstanceListenerExample}'s bound instance method listens for it. */
public record InstanceTestEvent(ServerPlayer player) implements Event {
}
