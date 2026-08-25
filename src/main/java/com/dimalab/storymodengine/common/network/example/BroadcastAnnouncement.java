package com.dimalab.storymodengine.common.network.example;

import com.dimalab.storymodengine.common.logging.EngineLog;
import com.dimalab.storymodengine.api.network.ClientboundPacket;
import com.dimalab.storymodengine.api.network.PacketHandler;
import com.dimalab.storymodengine.api.network.annotation.Packet;
import com.dimalab.storymodengine.common.network.context.PacketContext;
import org.joml.Vector3f;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Demonstrates {@code String}/{@code List<T>}/{@code Optional<T>} (this engine's nullable-value
 * convention)/a JOML math type all serializing automatically in one record, sent {@code sendToAll}.
 */
@Packet
public record BroadcastAnnouncement(
        String message,
        List<UUID> mentionedPlayers,
        Optional<Integer> color,
        Vector3f origin
) implements ClientboundPacket, PacketHandler {

    @Override
    public void handle(PacketContext context) {
        EngineLog.channel("Network").info(
                "Broadcast received: \"{}\" mentioning {} player(s), origin ({}, {}, {}), color {}",
                message, mentionedPlayers.size(), origin.x, origin.y, origin.z,
                color.map(Integer::toHexString).orElse("default")).toChat();
    }
}
