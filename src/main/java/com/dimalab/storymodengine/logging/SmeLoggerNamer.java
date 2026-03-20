package com.dimalab.storymodengine.logging;

import com.dimalab.storymodengine.StoryModEngine;

/**
 * Builds consistent SLF4J/Log4j2 logger names.
 * Logger names follow: {@code storymodengine} or {@code storymodengine.<subsystem>}.
 */
public final class SmeLoggerNamer {
    private SmeLoggerNamer() {}

    public static String rootName() {
        return StoryModEngine.MODID;
    }

    public static String loggerName(String subsystem) {
        if (subsystem == null || subsystem.isBlank()) return rootName();
        return rootName() + "." + sanitize(subsystem);
    }

    private static String sanitize(String subsystem) {
        // Keep logger names compact & consistent (no spaces).
        return subsystem.trim().replaceAll("\\s+", "_");
    }
}

