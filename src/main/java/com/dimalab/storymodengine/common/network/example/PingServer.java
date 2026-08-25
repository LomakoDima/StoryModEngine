package com.dimalab.storymodengine.common.network.example;

import com.dimalab.storymodengine.common.logging.EngineLog;
import com.dimalab.storymodengine.common.network.Network;
import com.dimalab.storymodengine.api.network.PacketHandler;
import com.dimalab.storymodengine.api.network.ServerboundPacket;
import com.dimalab.storymodengine.api.network.annotation.Packet;
import com.dimalab.storymodengine.common.network.context.PacketContext;
import net.minecraft.server.level.ServerPlayer;

/**
 * Client → server round-trip demo: carries the client's own send time, replies with {@link
 * PongClient} to the same player. {@code @Packet} + {@code ServerboundPacket} + {@code
 * PacketHandler} on one record is the entire "developer describes data and behavior" story —
 * nothing else is written anywhere to make this work once {@code EngineBootstrap.init(...)} runs.
 */
@Packet
public record PingServer(long clientTimeMillis) implements ServerboundPacket, PacketHandler {

    @Override
    public void handle(PacketContext context) {
        ServerPlayer sender = context.sender();
        if (sender == null) {
            // ServerboundPacket already guarantees this only decodes server-side, but a handler
            // should never assume a spoof-proof direction check also means "sender is populated" —
            // Context#getSender() can still be null in edge cases (verified against source).
            EngineLog.channel("Network").warn("PingServer received with no sender — dropping");
            return;
        }
        Network.sendToPlayer(sender, new PongClient(clientTimeMillis, System.currentTimeMillis()));
    }
}
