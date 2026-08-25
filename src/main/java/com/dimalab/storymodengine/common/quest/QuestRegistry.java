package com.dimalab.storymodengine.common.quest;

import net.minecraft.resources.ResourceLocation;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/** A simple {@code id -> QuestDefinition} lookup — populated automatically via {@code @AutoQuest} discovery, or by a JSON-loaded quest. Same shape as {@code dialogue.registry.DialogueRegistry}/{@code cinematic.registry.CutsceneRegistry}. */
public final class QuestRegistry {

    private static final Map<ResourceLocation, QuestDefinition> DEFINITIONS = new ConcurrentHashMap<>();

    private QuestRegistry() {
    }

    public static void register(QuestDefinition definition) {
        DEFINITIONS.put(definition.id(), definition);
    }

    public static QuestDefinition get(ResourceLocation id) {
        return DEFINITIONS.get(id);
    }

    public static Map<ResourceLocation, QuestDefinition> all() {
        return Map.copyOf(DEFINITIONS);
    }
}
