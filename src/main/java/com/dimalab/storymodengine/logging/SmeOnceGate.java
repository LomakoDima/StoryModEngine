package com.dimalab.storymodengine.logging;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Session-scoped "log once" gate.
 */
public final class SmeOnceGate {
    private SmeOnceGate() {}

    private static final Set<String> ONCE_KEYS = ConcurrentHashMap.newKeySet();

    public static boolean markFirst(String subsystem, String key) {
        if (key == null || key.isBlank()) return true;
        return ONCE_KEYS.add(subsystemKey(subsystem, key));
    }

    private static String subsystemKey(String subsystem, String key) {
        String s = (subsystem == null) ? "" : subsystem.trim();
        return Objects.requireNonNullElse(s, "") + "\n" + key.trim();
    }
}

