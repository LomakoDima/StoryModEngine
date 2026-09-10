package com.dimalab.storymodengine.common.shop.network;

import com.dimalab.storymodengine.api.network.PacketHandler;
import com.dimalab.storymodengine.api.network.ServerboundPacket;
import com.dimalab.storymodengine.api.network.annotation.Packet;
import com.dimalab.storymodengine.common.logging.EngineLog;
import com.dimalab.storymodengine.common.network.context.PacketContext;
import com.dimalab.storymodengine.common.shop.ShopSystem;
import net.minecraft.server.level.ServerPlayer;

/**
 * "I'd like to buy {@code quantity} of catalog entry {@code entryIndex} from shop {@code shopId}" —
 * a request, not a fact: {@link ShopSystem#buy} re-validates everything against the player's live
 * inventory server-side and never trusts {@code quantity} or the client's own idea of its balance
 * (the same discipline {@code DialogueChoicePacket} already follows for its own pick).
 */
@Packet
public record ShopBuyPacket(String shopId, int entryIndex, int quantity) implements ServerboundPacket, PacketHandler {

    @Override
    public void handle(PacketContext context) {
        ServerPlayer sender = context.sender();
        if (sender == null) {
            EngineLog.channel("Shop").warn("ShopBuyPacket received with no sender — dropping");
            return;
        }
        ShopSystem.buy(sender, shopId, entryIndex, quantity);
    }
}
