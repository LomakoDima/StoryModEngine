package com.dimalab.storymodengine.common.event.example;

import com.dimalab.storymodengine.api.event.Event;
import net.minecraft.server.level.ServerPlayer;
import org.joml.Vector3f;

/**
 * Posted server-side by {@link EventTestPacket#handle} once a client's {@code /storymodengine
 * eventtest network} packet arrives — proves the network integration (an existing {@code @Packet}
 * triggers {@code Events.post(...)}), the math integration ({@link #position()} crossed the network
 * through the existing {@code SerializerRegistry} Vector3f serializer, no new serialization code),
 * and the capabilities integration ({@link EventTestListeners#onNetworkTriggered} reacts to this by
 * mutating the player's existing {@code StoryPlayerData}).
 */
public record NetworkTriggeredTestEvent(ServerPlayer player, Vector3f position) implements Event {
}
