package com.dimalab.storymodengine.common.logging;

import com.dimalab.storymodengine.api.logging.LogLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.Collection;

/**
 * The handle every {@link EngineLogger} level method returns: the console/log-file write has
 * already happened by the time you have one of these — an {@code EngineLog.info("...")} call with
 * nothing chained onto it <em>is</em> "console only", satisfying that requirement with zero extra
 * syntax. Chaining {@link #toChat()}/{@link #toChat(ServerPlayer)}/{@link #toChat(Collection)}
 * additionally broadcasts the same message to chat — "console and chat simultaneously" is just
 * calling one of those, not a different logging mode.
 *
 * <p>A level filtered out by {@link EngineLogger#setMinimumLevel} returns {@link #noop()}, whose
 * chat methods are no-ops — filtering suppresses both destinations uniformly, with nothing extra
 * for a caller to check.
 */
public final class LogEntry {

    private static final LogEntry NOOP = new LogEntry(null, null);

    private final LogLevel level;
    private Component chatComponent;

    LogEntry(LogLevel level, Component chatComponent) {
        this.level = level;
        this.chatComponent = chatComponent;
    }

    static LogEntry noop() {
        return NOOP;
    }

    /** Overrides the chat message's style — has no effect on the console line, which was already written as plain text. No-op on {@link #noop()}. */
    public LogEntry style(Style style) {
        if (this == NOOP) {
            return this;
        }
        chatComponent = chatComponent.copy().setStyle(style);
        return this;
    }

    /** Shorthand for {@code style(Style.EMPTY.withColor(color))}. */
    public LogEntry color(ChatFormatting color) {
        return style(Style.EMPTY.withColor(color));
    }

    /** Broadcasts to every player currently on the server. Silently does nothing if no server is running or no players are online. */
    public LogEntry toChat() {
        if (this == NOOP || !LoggingConfig.isChatEnabled()) {
            return this;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return this;
        }
        Component message = chatComponent;
        runOnServerThread(server, () -> server.getPlayerList().broadcastSystemMessage(message, false));
        return this;
    }

    /** Sends to one player. Safe to call with a player who has since disconnected — the send is simply a no-op then. */
    public LogEntry toChat(ServerPlayer player) {
        if (this == NOOP || !LoggingConfig.isChatEnabled() || player == null) {
            return this;
        }
        Component message = chatComponent;
        MinecraftServer server = player.getServer();
        if (server == null) {
            player.sendSystemMessage(message);
        } else {
            runOnServerThread(server, () -> player.sendSystemMessage(message));
        }
        return this;
    }

    /** Sends to each player in {@code players} — e.g. a team, a party, everyone in one dimension. */
    public LogEntry toChat(Collection<ServerPlayer> players) {
        if (this == NOOP || !LoggingConfig.isChatEnabled() || players.isEmpty()) {
            return this;
        }
        for (ServerPlayer player : players) {
            toChat(player);
        }
        return this;
    }

    /** Runs {@code task} on the server thread — inline if already there, scheduled via {@code execute} otherwise, so chat dispatch is safe from any thread. */
    private static void runOnServerThread(MinecraftServer server, Runnable task) {
        if (server.isSameThread()) {
            task.run();
        } else {
            server.execute(task);
        }
    }

    public LogLevel level() {
        return level;
    }
}
