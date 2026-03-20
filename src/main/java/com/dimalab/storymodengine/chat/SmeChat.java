package com.dimalab.storymodengine.chat;

import com.dimalab.storymodengine.StoryModEngine;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.helpers.MessageFormatter;

/**
 * Player-facing chat messaging system.
 *
 * This module is intentionally independent from logging/debug systems.
 */
public final class SmeChat {
    private SmeChat() {}

    public static void broadcast(String message, Object... args) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        MutableComponent chatMessage = buildMessage(message, args);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(chatMessage);
        }
    }

    public static void toPlayer(ServerPlayer player, String message, Object... args) {
        if (player == null) return;
        player.sendSystemMessage(buildMessage(message, args));
    }

    private static MutableComponent buildMessage(String message, Object... args) {
        String formatted = MessageFormatter.arrayFormat(message, args).getMessage();

        return Component.literal("[")
                .withStyle(SmeChatPalette.BRACKETS)
                .append(Component.literal(StoryModEngine.MODID).withStyle(SmeChatPalette.MOD_NAME))
                .append(Component.literal("]").withStyle(SmeChatPalette.BRACKETS))
                .append(Component.literal(" ").withStyle(SmeChatPalette.MESSAGE))
                .append(Component.literal(formatted).withStyle(SmeChatPalette.MESSAGE));
    }
}

