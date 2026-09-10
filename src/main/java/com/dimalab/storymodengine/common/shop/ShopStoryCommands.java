package com.dimalab.storymodengine.common.shop;

import com.dimalab.storymodengine.api.scripting.annotation.StoryCommand;
import net.minecraft.server.level.ServerPlayer;

/**
 * The script-facing side of the shop system — a plain {@code @StoryCommand}, not a dedicated
 * statement (unlike {@code start dialogue <id>}), since opening a shop needs no AST-level special
 * form beyond a name.
 */
public final class ShopStoryCommands {

    private ShopStoryCommands() {
    }

    @StoryCommand("open_shop")
    public static void openShop(ServerPlayer player, String shopId) {
        ShopSystem.open(player, shopId);
    }
}
