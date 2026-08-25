package com.dimalab.storymodengine.common.logging;

import com.dimalab.storymodengine.api.logging.LogLevel;
/**
 * The two global knobs every {@link EngineLogger} channel respects, regardless of which mod
 * created it — plain runtime-settable fields, not a {@code ForgeConfigSpec}: the logging system
 * itself must not depend on any one mod's own config file to stay usable by others (see
 * {@code ARCHITECTURE.md}). A mod that wants these persisted just reads its own config during
 * setup and calls these setters — that's the intended integration point, not a competing config
 * registration here.
 */
public final class LoggingConfig {

    private static volatile boolean chatEnabled = true;
    private static volatile LogLevel defaultLevel = LogLevel.INFO;

    private LoggingConfig() {
    }

    /** Global chat kill-switch: when {@code false}, every channel's {@code LogEntry.toChat*} call becomes a no-op. Console/log-file output is unaffected. */
    public static void setChatEnabled(boolean enabled) {
        chatEnabled = enabled;
    }

    public static boolean isChatEnabled() {
        return chatEnabled;
    }

    /** The minimum level newly-created channels start at (see {@link EngineLogger#of}); existing channels are unaffected — use {@link EngineLogger#setMinimumLevel} for those. */
    public static void setDefaultLevel(LogLevel level) {
        defaultLevel = level;
    }

    public static LogLevel defaultLevel() {
        return defaultLevel;
    }
}
