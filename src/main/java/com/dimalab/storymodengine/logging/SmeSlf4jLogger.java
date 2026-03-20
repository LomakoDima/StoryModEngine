package com.dimalab.storymodengine.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pure SLF4J logging adapter (no once logic, no chat).
 */
public final class SmeSlf4jLogger {
    private SmeSlf4jLogger() {}

    public static Logger get(String subsystem) {
        return LoggerFactory.getLogger(SmeLoggerNamer.loggerName(subsystem));
    }

    public static Logger get(LogSubsystem subsystem) {
        return get(subsystem == null ? null : subsystem.id);
    }

    public static void debug(String subsystem, String message, Object... args) {
        Logger l = get(subsystem);
        if (l.isDebugEnabled()) l.debug(message, args);
    }

    public static void info(String subsystem, String message, Object... args) {
        get(subsystem).info(message, args);
    }

    public static void warn(String subsystem, String message, Object... args) {
        get(subsystem).warn(message, args);
    }

    public static void error(String subsystem, String message, Object... args) {
        get(subsystem).error(message, args);
    }

    public static void error(String subsystem, String message, Throwable t) {
        get(subsystem).error(message, t);
    }
}

