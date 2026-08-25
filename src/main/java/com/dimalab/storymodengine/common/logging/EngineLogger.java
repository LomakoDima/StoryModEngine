package com.dimalab.storymodengine.common.logging;

import com.dimalab.storymodengine.api.logging.LogLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.MessageFormatter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A named logging channel — one source/module tag, one console logger, one independent level
 * filter. {@link EngineLog} is just the pre-built channel named {@code "StoryModEngine"}; any
 * other mod gets the exact same capability with {@code EngineLogger.of("TheirModName")}, which is
 * the whole point of keeping this class (and the rest of {@code logging}) free of any dependency
 * on {@code content}/{@code resource}/{@code math} or StoryModEngine's own modid — see
 * {@code ARCHITECTURE.md}.
 *
 * <p>Console output goes through a plain {@code org.slf4j.Logger} named after the channel — Log4j2's
 * own pattern layout already brackets the logger name in every line Minecraft/Forge prints (the
 * same mechanism behind every existing {@code [modid/CATEGORY]} line in the log), so channel
 * identification in the console/log file comes for free rather than being hand-formatted here.
 * Placeholder substitution ({@code "{}"}) reuses SLF4J's own {@code MessageFormatter} rather than a
 * bespoke templating step.
 *
 * <p>Thread-safe: {@link #setMinimumLevel} is a volatile write, the channel registry is a
 * {@code ConcurrentHashMap}, and the underlying SLF4J logger is thread-safe by contract. Chat
 * dispatch's own thread-safety is handled in {@link LogEntry}.
 */
public final class EngineLogger {

    private static final Map<String, EngineLogger> CHANNELS = new ConcurrentHashMap<>();

    private final String name;
    private final Logger backing;
    private volatile LogLevel minimumLevel;
    private volatile ChatTemplate chatTemplate = ChatTemplate.DEFAULT;

    private EngineLogger(String name) {
        this.name = name;
        this.backing = LoggerFactory.getLogger(name);
        this.minimumLevel = LoggingConfig.defaultLevel();
    }

    /** Returns the channel named {@code name}, creating it on first use. Safe to call repeatedly — the same instance comes back every time. */
    public static EngineLogger of(String name) {
        return CHANNELS.computeIfAbsent(name, EngineLogger::new);
    }

    public String name() {
        return name;
    }

    public void setMinimumLevel(LogLevel level) {
        this.minimumLevel = level;
    }

    public LogLevel minimumLevel() {
        return minimumLevel;
    }

    /** How this channel's {@code [Name]: } prefix and message segment are styled in chat — {@link ChatTemplate#DEFAULT} unless set. */
    public void setChatTemplate(ChatTemplate template) {
        this.chatTemplate = template;
    }

    public ChatTemplate chatTemplate() {
        return chatTemplate;
    }

    public LogEntry trace(String message, Object... args) {
        return log(LogLevel.TRACE, message, args);
    }

    public LogEntry debug(String message, Object... args) {
        return log(LogLevel.DEBUG, message, args);
    }

    public LogEntry info(String message, Object... args) {
        return log(LogLevel.INFO, message, args);
    }

    public LogEntry success(String message, Object... args) {
        return log(LogLevel.SUCCESS, message, args);
    }

    public LogEntry warn(String message, Object... args) {
        return log(LogLevel.WARNING, message, args);
    }

    public LogEntry error(String message, Object... args) {
        return log(LogLevel.ERROR, message, args);
    }

    /** Logs at ERROR with a stack trace attached to the console line — the chat message carries only {@code message}. */
    public LogEntry error(String message, Throwable cause) {
        if (!accepts(LogLevel.ERROR)) {
            return LogEntry.noop();
        }
        backing.error(message, cause);
        return new LogEntry(LogLevel.ERROR, renderChat(LogLevel.ERROR, message));
    }

    public LogEntry trace(Component message) {
        return log(LogLevel.TRACE, message);
    }

    public LogEntry debug(Component message) {
        return log(LogLevel.DEBUG, message);
    }

    public LogEntry info(Component message) {
        return log(LogLevel.INFO, message);
    }

    public LogEntry success(Component message) {
        return log(LogLevel.SUCCESS, message);
    }

    public LogEntry warn(Component message) {
        return log(LogLevel.WARNING, message);
    }

    public LogEntry error(Component message) {
        return log(LogLevel.ERROR, message);
    }

    public LogEntry log(LogLevel level, String message, Object... args) {
        if (!accepts(level)) {
            return LogEntry.noop();
        }
        String formatted = args.length == 0 ? message : MessageFormatter.arrayFormat(message, args).getMessage();
        writeConsole(level, formatted);
        return new LogEntry(level, renderChat(level, formatted));
    }

    public LogEntry log(LogLevel level, Component message) {
        if (!accepts(level)) {
            return LogEntry.noop();
        }
        writeConsole(level, message.getString());
        return new LogEntry(level, renderChat(level, message));
    }

    private boolean accepts(LogLevel level) {
        return level.severity() >= minimumLevel.severity();
    }

    private void writeConsole(LogLevel level, String formatted) {
        switch (level) {
            case TRACE -> backing.trace(formatted);
            case DEBUG -> backing.debug(formatted);
            case INFO -> backing.info(formatted);
            case SUCCESS -> backing.info("[SUCCESS] {}", formatted);
            case WARNING -> backing.warn(formatted);
            case ERROR -> backing.error(formatted);
        }
    }

    private MutableComponent renderChat(LogLevel level, String text) {
        return renderChat(level, Component.literal(text));
    }

    private MutableComponent renderChat(LogLevel level, Component message) {
        return chatTemplate.render(name, level, message);
    }
}
