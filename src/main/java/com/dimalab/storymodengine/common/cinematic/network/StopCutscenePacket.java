package com.dimalab.storymodengine.common.cinematic.network;

import com.dimalab.storymodengine.client.cinematic.ClientCutscenePlayer;
import com.dimalab.storymodengine.api.network.ClientboundPacket;
import com.dimalab.storymodengine.api.network.PacketHandler;
import com.dimalab.storymodengine.api.network.annotation.Packet;
import com.dimalab.storymodengine.common.network.context.PacketContext;
import net.minecraft.resources.ResourceLocation;

/** Server-initiated early stop (explicit cancel, or replaced per {@code CinematicManager}'s "one active per player" policy). */
@Packet
public record StopCutscenePacket(ResourceLocation cutsceneId) implements ClientboundPacket, PacketHandler {

    @Override
    public void handle(PacketContext context) {
        ClientCutscenePlayer.stop(cutsceneId);
    }
}
