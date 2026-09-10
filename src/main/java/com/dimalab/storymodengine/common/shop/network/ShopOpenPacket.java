package com.dimalab.storymodengine.common.shop.network;

import com.dimalab.storymodengine.client.shop.ClientShopPlayer;
import com.dimalab.storymodengine.api.network.ClientboundPacket;
import com.dimalab.storymodengine.api.network.PacketHandler;
import com.dimalab.storymodengine.api.network.annotation.Packet;
import com.dimalab.storymodengine.common.network.context.PacketContext;

import java.util.List;

/**
 * "Here's the catalog and your current balance" — sent once when a shop opens for a player, and
 * again after every {@code ShopBuyPacket} (whether it succeeded or not) so the screen's balance and
 * button states stay in sync with the server's own authoritative inventory count. One packet type
 * for both cases, mirroring {@code DialogueStepPacket}'s own "resend the whole current state rather
 * than a delta" choice — a shop's catalog is small, so there's no real cost to always sending it all.
 */
@Packet
public record ShopOpenPacket(String shopId, String currencyItemId, List<ShopEntrySnapshot> entries, int balance)
        implements ClientboundPacket, PacketHandler {

    @Override
    public void handle(PacketContext context) {
        ClientShopPlayer.onOpen(this);
    }
}
