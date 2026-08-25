package com.dimalab.storymodengine.common.cinematic.registry;

import com.dimalab.storymodengine.common.cinematic.CutsceneDefinition;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A simple {@code id → CutsceneDefinition} lookup — populated automatically via {@code
 * @AutoCutscene} discovery, or by hand. Loaded identically on both logical sides (definitions are
 * plain Java, no Forge dependency), which is what lets the client resolve the same definition the
 * server merely referenced by id in {@code PlayCutscenePacket}.
 */
public final class CutsceneRegistry {

    private static final Map<ResourceLocation, CutsceneDefinition> DEFINITIONS = new ConcurrentHashMap<>();

    private CutsceneRegistry() {
    }

    public static void register(CutsceneDefinition definition) {
        DEFINITIONS.put(definition.id(), definition);
    }

    public static CutsceneDefinition get(ResourceLocation id) {
        return DEFINITIONS.get(id);
    }
}
