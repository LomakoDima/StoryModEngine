package com.dimalab.storymodengine.logging;

import org.slf4j.Logger;

/**
 * Central logging entrypoint for StoryModEngine.
 */
public final class SmeLog {
    private SmeLog() {}

    public static Logger root() {
        return SmeSlf4jLogger.get((String) null);
    }

    public static Logger get(String subsystem) {
        return SmeSlf4jLogger.get(subsystem);
    }

    public static Logger get(LogSubsystem subsystem) {
        return SmeSlf4jLogger.get(subsystem);
    }

    public static void debug(String subsystem, String message, Object... args) {
        SmeSlf4jLogger.debug(subsystem, message, args);
    }

    public static void chatDebug(String subsystem, String message, Object... args) {
        SmeChatBridge.debug(subsystem, message, args);
    }

    public static void debug(LogSubsystem subsystem, String message, Object... args) {
        debug(subsystem == null ? null : subsystem.id, message, args);
    }

    public static void info(String subsystem, String message, Object... args) {
        SmeSlf4jLogger.info(subsystem, message, args);
    }

    public static void chatInfo(String subsystem, String message, Object... args) {
        SmeChatBridge.info(subsystem, message, args);
    }

    public static void chatInfo(LogSubsystem subsystem, String message, Object... args) {
        chatInfo(subsystem == null ? null : subsystem.label, message, args);
    }

    public static void info(LogSubsystem subsystem, String message, Object... args) {
        info(subsystem == null ? null : subsystem.id, message, args);
    }

    public static void warn(String subsystem, String message, Object... args) {
        SmeSlf4jLogger.warn(subsystem, message, args);
    }

    public static void chatWarn(String subsystem, String message, Object... args) {
        SmeChatBridge.warn(subsystem, message, args);
    }

    public static void warn(LogSubsystem subsystem, String message, Object... args) {
        warn(subsystem == null ? null : subsystem.id, message, args);
    }

    public static void chatWarn(LogSubsystem subsystem, String message, Object... args) {
        chatWarn(subsystem == null ? null : subsystem.label, message, args);
    }

    public static void error(String subsystem, String message, Object... args) {
        SmeSlf4jLogger.error(subsystem, message, args);
    }

    public static void chatError(String subsystem, String message, Object... args) {
        SmeChatBridge.error(subsystem, message, args);
    }

    public static void error(LogSubsystem subsystem, String message, Object... args) {
        error(subsystem == null ? null : subsystem.id, message, args);
    }

    public static void chatError(LogSubsystem subsystem, String message, Object... args) {
        chatError(subsystem == null ? null : subsystem.label, message, args);
    }

    public static void error(String subsystem, String message, Throwable t) {
        SmeSlf4jLogger.error(subsystem, message, t);
    }

    public static void error(LogSubsystem subsystem, String message, Throwable t) {
        error(subsystem == null ? null : subsystem.id, message, t);
    }

    /**
     * Logs the message only once per unique key (per game session).
     */
    public static void onceInfo(String subsystem, String key, String message, Object... args) {
        if (SmeOnceGate.markFirst(subsystem, key)) info(subsystem, message, args);
    }

    public static void onceInfo(LogSubsystem subsystem, String key, String message, Object... args) {
        onceInfo(subsystem == null ? null : subsystem.id, key, message, args);
    }

    /**
     * Logs the message only once per unique key (per game session).
     */
    public static void onceWarn(String subsystem, String key, String message, Object... args) {
        if (SmeOnceGate.markFirst(subsystem, key)) warn(subsystem, message, args);
    }

    public static void onceWarn(LogSubsystem subsystem, String key, String message, Object... args) {
        onceWarn(subsystem == null ? null : subsystem.id, key, message, args);
    }
}

