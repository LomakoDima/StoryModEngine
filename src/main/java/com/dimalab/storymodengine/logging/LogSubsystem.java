package com.dimalab.storymodengine.logging;

public enum LogSubsystem {
    LIFECYCLE("lifecycle", "ЖИЗНЕННЫЙ_ЦИКЛ"),
    CONFIG("config", "КОНФИГ"),
    EVENTS("events", "СОБЫТИЯ"),
    REGISTRY("registry", "РЕГИСТРЫ"),
    NETWORK("network", "СЕТЬ"),
    SCRIPTING("scripting", "СКРИПТЫ"),
    IO("io", "ВВОД_ВЫВОД");

    public final String id;
    /**
     * Human-readable / chat label (we use it for colored [SUBSYSTEM] in chat).
     */
    public final String label;

    LogSubsystem(String id, String label) {
        this.id = id;
        this.label = label;
    }
}

