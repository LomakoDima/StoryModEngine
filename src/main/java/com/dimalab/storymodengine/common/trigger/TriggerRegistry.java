package com.dimalab.storymodengine.common.trigger;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** A simple {@code id -> Trigger} lookup — populated automatically via {@code @AutoTrigger} discovery. Same shape as {@code quest.QuestRegistry}. */
public final class TriggerRegistry {

    private static final Map<ResourceLocation, Trigger> TRIGGERS = new ConcurrentHashMap<>();

    private TriggerRegistry() {
    }

    public static void register(Trigger trigger) {
        TRIGGERS.put(trigger.id(), trigger);
    }

    public static Trigger get(ResourceLocation id) {
        return TRIGGERS.get(id);
    }

    public static Map<ResourceLocation, Trigger> all() {
        return Map.copyOf(TRIGGERS);
    }
}
