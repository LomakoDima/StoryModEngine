package com.dimalab.storymodengine.common.event.example;

import com.dimalab.storymodengine.api.event.Event;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/** Posted by {@code /storymodengine eventtest exception} — the middle of three listeners deliberately throws. */
public record ExceptionTestEvent(ServerPlayer player, List<String> executionOrder) implements Event {
}
