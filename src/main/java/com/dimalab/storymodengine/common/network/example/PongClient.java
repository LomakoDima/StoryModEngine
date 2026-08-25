package com.dimalab.storymodengine.common.network.example;

import com.dimalab.storymodengine.common.logging.EngineLog;
import com.dimalab.storymodengine.api.network.ClientboundPacket;
import com.dimalab.storymodengine.api.network.PacketHandler;
import com.dimalab.storymodengine.api.network.annotation.Packet;
import com.dimalab.storymodengine.common.network.context.PacketContext;

/** The other half of {@link PingServer}'s round trip — server → client, prints the latency. */
@Packet
public record PongClient(long clientTimeMillis, long serverTimeMillis) implements ClientboundPacket, PacketHandler {

    @Override
    public void handle(PacketContext context) {
        long roundTripMillis = System.currentTimeMillis() - clientTimeMillis;
        EngineLog.channel("Network").success("Pong! Round trip {} ms (server time {})", roundTripMillis, serverTimeMillis).toChat();
    }
}
