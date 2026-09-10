package com.dimalab.storymodengine.client.shop;

import com.dimalab.storymodengine.common.shop.network.ShopOpenPacket;
import net.minecraft.client.Minecraft;

/**
 * The single client-side driver reacting to {@link ShopOpenPacket} — mirrors {@code
 * dialogue.ClientDialoguePlayer}'s own role, simplified since a shop has only one screen shape (no
 * line/choice split): open it if it isn't already showing for this shop, otherwise update the
 * already-open screen's entries/balance in place so a buy's refresh doesn't visibly flicker/reopen.
 */
public final class ClientShopPlayer {

    private ClientShopPlayer() {
    }

    public static void onOpen(ShopOpenPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof ShopScreen shopScreen && shopScreen.matches(packet.shopId())) {
            shopScreen.update(packet);
        } else {
            minecraft.setScreen(new ShopScreen(packet));
        }
    }
}
