package com.dimalab.storymodengine.common.event.example;

import com.dimalab.storymodengine.common.event.Events;
import com.dimalab.storymodengine.api.network.PacketHandler;
import com.dimalab.storymodengine.api.network.ServerboundPacket;
import com.dimalab.storymodengine.api.network.annotation.Packet;
import com.dimalab.storymodengine.common.network.context.PacketContext;
import org.joml.Vector3f;

/**
 * The network half of {@code /sme eventtest network} — nothing here is new
 * infrastructure, it is the existing {@code network} system end to end: {@code @Packet} for
 * discovery, {@code ServerboundPacket} for direction, {@code PacketHandler#handle} for behavior,
 * and the already-registered {@code Vector3f} serializer for the payload. {@link #handle} is the
 * bridge from "a packet arrived" to "an engine event happened" — {@code EventBus} itself never
 * needs to know packets exist.
 */
@Packet
public record EventTestPacket(Vector3f position) implements ServerboundPacket, PacketHandler {

    @Override
    public void handle(PacketContext context) {
        Events.post(new NetworkTriggeredTestEvent(context.sender(), position));
    }
}
