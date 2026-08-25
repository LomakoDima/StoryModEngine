package com.dimalab.storymodengine.api.logging;

import net.minecraft.ChatFormatting;

/**
 * The six levels {@link EngineLogger} supports, ordered by {@link #severity()} for filtering.
 * {@code SUCCESS} shares {@code INFO}'s severity — it's a stylistic variant ("this informational
 * thing went well"), not a distinct tier — so a channel filtered to {@code INFO} shows both.
 *
 * <p>SLF4J itself has no {@code SUCCESS} level; console output for it goes through
 * {@code Logger.info(...)} with a {@code [SUCCESS]} marker in the message text (see
 * {@link EngineLogger}) rather than inventing a backend level SLF4J/Log4j don't understand.
 */
public enum LogLevel {

    TRACE(0, "TRACE", ChatFormatting.DARK_GRAY),
    DEBUG(1, "DEBUG", ChatFormatting.GRAY),
    INFO(2, "INFO", ChatFormatting.WHITE),
    SUCCESS(2, "SUCCESS", ChatFormatting.GREEN),
    WARNING(3, "WARN", ChatFormatting.YELLOW),
    ERROR(4, "ERROR", ChatFormatting.RED);

    private final int severity;
    private final String label;
    private final ChatFormatting color;

    LogLevel(int severity, String label, ChatFormatting color) {
        this.severity = severity;
        this.label = label;
        this.color = color;
    }

    /** Higher is more severe; a channel filtered at level {@code L} shows everything with {@code severity() >= L.severity()}. */
    public int severity() {
        return severity;
    }

    public String label() {
        return label;
    }

    /** The default chat color for this level — a channel-level {@code .color(...)} override wins over this. */
    public ChatFormatting color() {
        return color;
    }
}
