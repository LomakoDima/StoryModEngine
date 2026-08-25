package com.dimalab.storymodengine.common.cinematic.network;

import com.dimalab.storymodengine.client.cinematic.ClientTitleCardPlayer;
import com.dimalab.storymodengine.api.network.ClientboundPacket;
import com.dimalab.storymodengine.api.network.PacketHandler;
import com.dimalab.storymodengine.api.network.annotation.Packet;
import com.dimalab.storymodengine.common.network.context.PacketContext;

/** Server-initiated early stop (explicit cancel, or replaced by a new title card starting) — mirrors {@code StopCutscenePacket}. */
@Packet
public record StopTitleCardPacket() implements ClientboundPacket, PacketHandler {

    @Override
    public void handle(PacketContext context) {
        ClientTitleCardPlayer.stop();
    }
}
