package com.dimalab.storymodengine.common.entity.npc;

import com.dimalab.storymodengine.common.logging.EngineLog;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code name -> NpcDefinition}, populated by {@code .sme}'s {@code npc { }} declarations. Same shape
 * as {@code quest.QuestRegistry}/{@code dialogue.registry.DialogueRegistry}, except keyed by the
 * NPC's plain display-string name rather than a namespaced {@code ResourceLocation} — see {@link
 * NpcDefinition}'s own doc for why.
 */
public final class NpcDefinitionRegistry {

    private static final Map<String, NpcDefinition> DEFINITIONS = new ConcurrentHashMap<>();

    private NpcDefinitionRegistry() {
    }

    /** Two different stories declaring the same name is a real possibility with no cross-file namespace to separate them — logged, not thrown; the later declaration wins. */
    public static void register(NpcDefinition definition) {
        if (DEFINITIONS.containsKey(definition.name())) {
            EngineLog.channel("Npc").warn("npc '{}' declared more than once — the later declaration replaces the earlier one", definition.name());
        }
        DEFINITIONS.put(definition.name(), definition);
    }

    public static NpcDefinition get(String name) {
        return DEFINITIONS.get(name);
    }

    public static Map<String, NpcDefinition> all() {
        return Map.copyOf(DEFINITIONS);
    }
}
