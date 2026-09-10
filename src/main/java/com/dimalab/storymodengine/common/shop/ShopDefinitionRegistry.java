package com.dimalab.storymodengine.common.shop;

import com.dimalab.storymodengine.common.logging.EngineLog;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code name -> ShopDefinition}, populated by {@code .sme}'s {@code shop { }} declarations. Same
 * shape as {@code npc.NpcDefinitionRegistry}, keyed by the shop's own plain name.
 */
public final class ShopDefinitionRegistry {

    private static final Map<String, ShopDefinition> DEFINITIONS = new ConcurrentHashMap<>();

    private ShopDefinitionRegistry() {
    }

    /** Two different stories declaring the same name is a real possibility with no cross-file namespace to separate them — logged, not thrown; the later declaration wins. */
    public static void register(ShopDefinition definition) {
        if (DEFINITIONS.containsKey(definition.name())) {
            EngineLog.channel("Shop").warn("shop '{}' declared more than once — the later declaration replaces the earlier one", definition.name());
        }
        DEFINITIONS.put(definition.name(), definition);
    }

    public static ShopDefinition get(String name) {
        return DEFINITIONS.get(name);
    }

    public static Map<String, ShopDefinition> all() {
        return Map.copyOf(DEFINITIONS);
    }
}
