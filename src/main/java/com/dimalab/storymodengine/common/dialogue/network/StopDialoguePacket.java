package com.dimalab.storymodengine.common.dialogue.network;

import com.dimalab.storymodengine.client.dialogue.ClientDialoguePlayer;
import com.dimalab.storymodengine.api.network.ClientboundPacket;
import com.dimalab.storymodengine.api.network.PacketHandler;
import com.dimalab.storymodengine.api.network.annotation.Packet;
import com.dimalab.storymodengine.common.network.context.PacketContext;

/** Closes {@link com.dimalab.storymodengine.client.dialogue.DialogueWindow}, if open — completion, cancellation, and failure all funnel through this one packet. */
@Packet
public record StopDialoguePacket() implements ClientboundPacket, PacketHandler {

    @Override
    public void handle(PacketContext context) {
        ClientDialoguePlayer.onStop();
    }
}
