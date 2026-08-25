package com.dimalab.storymodengine.common.quest.network;

import com.dimalab.storymodengine.api.network.ClientboundPacket;
import com.dimalab.storymodengine.api.network.PacketHandler;
import com.dimalab.storymodengine.api.network.annotation.Packet;
import com.dimalab.storymodengine.common.network.context.PacketContext;
import com.dimalab.storymodengine.client.quest.QuestToast;

/**
 * A one-shot "something just happened" notification — the one genuinely new packet this system
 * adds, and only because a moment-in-time toast can't be expressed by the generic capability-sync
 * mechanism that already covers full quest *state* (see the design doc §8's own note on why). Sent
 * by {@code QuestNotificationBridge}, a server-side listener on the four {@code quest.event}
 * classes; carries pre-formatted text rather than structured data, since display is all this does.
 */
@Packet
public record QuestToastPacket(String message) implements ClientboundPacket, PacketHandler {

    @Override
    public void handle(PacketContext context) {
        QuestToast.show(message);
    }
}
