package com.dimalab.storymodengine.common.network.example;

import com.dimalab.storymodengine.common.logging.EngineLog;
import com.dimalab.storymodengine.api.network.ClientboundPacket;
import com.dimalab.storymodengine.api.network.PacketHandler;
import com.dimalab.storymodengine.api.network.annotation.Packet;
import com.dimalab.storymodengine.common.network.context.PacketContext;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * The exact packet from the task's own minimal-DX example — {@code UUID}/{@code Vec3}/{@code
 * float}, server → client, purely a demonstration handler (logs what it received).
 */
@Packet
public record SyncPlayerState(UUID playerId, Vec3 position, float health) implements ClientboundPacket, PacketHandler {

    @Override
    public void handle(PacketContext context) {
        EngineLog.channel("Network").info(
                "SyncPlayerState received: player={} pos=({}, {}, {}) health={}",
                playerId, position.x, position.y, position.z, health).toChat();
    }
}
