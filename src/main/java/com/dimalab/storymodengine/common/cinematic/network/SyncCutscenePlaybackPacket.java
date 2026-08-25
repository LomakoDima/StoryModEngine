package com.dimalab.storymodengine.common.cinematic.network;

import com.dimalab.storymodengine.client.cinematic.ClientCutscenePlayer;
import com.dimalab.storymodengine.api.network.ClientboundPacket;
import com.dimalab.storymodengine.api.network.PacketHandler;
import com.dimalab.storymodengine.api.network.annotation.Packet;
import com.dimalab.storymodengine.common.network.context.PacketContext;
import net.minecraft.resources.ResourceLocation;

/**
 * The server's authoritative answer to a {@link RequestCutsceneControlPacket} — always sent, never
 * only on success, so a rejected or clamped request (e.g. a forward seek refused by {@code
 * SkipPolicy.NON_SKIPPABLE}) is reflected on the client accurately instead of the client's own
 * optimistic guess silently drifting from what the server actually did.
 */
@Packet
public record SyncCutscenePlaybackPacket(ResourceLocation cutsceneId, int tick, float speed, boolean paused)
        implements ClientboundPacket, PacketHandler {

    @Override
    public void handle(PacketContext context) {
        ClientCutscenePlayer.applySync(cutsceneId, tick, speed, paused);
    }
}
