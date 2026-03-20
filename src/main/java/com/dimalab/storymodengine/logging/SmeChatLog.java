package com.dimalab.storymodengine.logging;

import com.dimalab.storymodengine.StoryModEngine;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.helpers.MessageFormatter;

/**
 * Chat-based logging.
 *
 * Prefix color scheme (legacy-style request):
 * - "§33" => treated as legacy color §3 (we use {@link ChatFormatting#DARK_AQUA})
 * - "§66" => treated as legacy color §6 (we use {@link ChatFormatting#GOLD})
 *
 * Result: [StoryModEngine] with brackets and mod name in different colors.
 */
public final class SmeChatLog {
    private SmeChatLog() {}

    public static void info(String message, Object... args) {
        // Info-сообщения в чате делаем белыми.
        broadcast(ChatFormatting.WHITE, message, args);
    }

    public static void warn(String message, Object... args) {
        broadcast(ChatFormatting.YELLOW, message, args);
    }

    public static void error(String message, Object... args) {
        broadcast(ChatFormatting.RED, message, args);
    }

    public static void debug(String message, Object... args) {
        broadcast(ChatFormatting.DARK_GRAY, message, args);
    }

    private static void broadcast(ChatFormatting levelColor, String message, Object... args) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        String formatted = MessageFormatter.arrayFormat(message, args).getMessage();

        MutableComponent prefix = Component.literal("[")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal("StoryModEngine").withStyle(ChatFormatting.AQUA))
                .append(Component.literal("]").withStyle(ChatFormatting.GOLD));

        // Use MutableComponent because append() is not available on the base Component type.
        MutableComponent chatMessage = prefix
                .append(Component.literal(" ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(formatted).withStyle(levelColor));

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(chatMessage);
        }
    }
}

