package com.dimalab.storymodengine.logging;

/**
 * Adds a "subsystem: " prefix and routes to {@link SmeChatLog}.
 */
public final class SmeChatBridge {
    private SmeChatBridge() {}

    private static String withSubsystemPrefix(String subsystem, String message) {
        if (subsystem == null || subsystem.isBlank()) return message;
        // Color only the subsystem tag; leave the rest of the message as-is.
        // Example result: §6[EVENTS]§r your message...
        return "§6" + subsystem + "§r " + message;
    }

    public static void debug(String subsystem, String message, Object... args) {
        SmeChatLog.debug(withSubsystemPrefix(subsystem, message), args);
    }

    public static void info(String subsystem, String message, Object... args) {
        SmeChatLog.info(withSubsystemPrefix(subsystem, message), args);
    }

    public static void warn(String subsystem, String message, Object... args) {
        SmeChatLog.warn(withSubsystemPrefix(subsystem, message), args);
    }

    public static void error(String subsystem, String message, Object... args) {
        SmeChatLog.error(withSubsystemPrefix(subsystem, message), args);
    }
}

