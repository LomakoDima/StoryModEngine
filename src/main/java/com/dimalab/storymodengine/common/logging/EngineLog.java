package com.dimalab.storymodengine.common.logging;

import net.minecraft.network.chat.Component;

/**
 * The three-line-example API: {@code EngineLog.info("Text Message");}. This is nothing but
 * StoryModEngine's own pre-built {@link EngineLogger} channel (named {@code "StoryModEngine"},
 * matching the {@code [StoryModEngine] ...} chat/console prefix) exposed as static methods so the
 * common case needs no channel lookup at all. Any other mod gets the identical capability under
 * its own name via {@code EngineLogger.of("TheirModName")} — see that class.
 *
 * <pre>{@code
 * EngineLog.info("Loaded {} recipes", count);           // console only
 * EngineLog.warn("Config value missing, using default"); // console only
 * EngineLog.success("World generated").toChat();         // console + every player
 * EngineLog.error("Failed to save {}", fileName).toChat(player); // console + one player
 * }</pre>
 */
public final class EngineLog {

    private static final EngineLogger CHANNEL = EngineLogger.of("StoryModEngine");

    private EngineLog() {
    }

    /** The underlying "StoryModEngine" channel — use this for {@link EngineLogger#setMinimumLevel}/{@link EngineLogger#setChatTemplate}. */
    public static EngineLogger channel() {
        return CHANNEL;
    }

    /**
     * Any other named channel — {@code EngineLogger.of(name)} under a shorter, discoverable name
     * next to the rest of this facade. {@code EngineLog.channel("Story").info("Chapter loaded")}
     * still gets the standard {@code [Story]: ...} prefix automatically; only the bracketed name
     * changes.
     */
    public static EngineLogger channel(String name) {
        return EngineLogger.of(name);
    }

    public static LogEntry trace(String message, Object... args) {
        return CHANNEL.trace(message, args);
    }

    public static LogEntry debug(String message, Object... args) {
        return CHANNEL.debug(message, args);
    }

    public static LogEntry info(String message, Object... args) {
        return CHANNEL.info(message, args);
    }

    public static LogEntry success(String message, Object... args) {
        return CHANNEL.success(message, args);
    }

    public static LogEntry warn(String message, Object... args) {
        return CHANNEL.warn(message, args);
    }

    public static LogEntry error(String message, Object... args) {
        return CHANNEL.error(message, args);
    }

    public static LogEntry error(String message, Throwable cause) {
        return CHANNEL.error(message, cause);
    }

    public static LogEntry trace(Component message) {
        return CHANNEL.trace(message);
    }

    public static LogEntry debug(Component message) {
        return CHANNEL.debug(message);
    }

    public static LogEntry info(Component message) {
        return CHANNEL.info(message);
    }

    public static LogEntry success(Component message) {
        return CHANNEL.success(message);
    }

    public static LogEntry warn(Component message) {
        return CHANNEL.warn(message);
    }

    public static LogEntry error(Component message) {
        return CHANNEL.error(message);
    }
}
