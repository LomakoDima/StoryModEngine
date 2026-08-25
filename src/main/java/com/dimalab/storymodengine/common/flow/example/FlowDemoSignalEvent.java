package com.dimalab.storymodengine.common.flow.example;

import com.dimalab.storymodengine.api.event.Event;
import net.minecraft.server.level.ServerPlayer;

/**
 * The custom event {@code /storymodengine flowdemo signal} posts and the demo's {@code
 * EventWaiter} step waits for — proves {@code EventWaiter} wakes on a genuine {@code Events.post}
 * rather than polling.
 */
public record FlowDemoSignalEvent(ServerPlayer player) implements Event {
}
