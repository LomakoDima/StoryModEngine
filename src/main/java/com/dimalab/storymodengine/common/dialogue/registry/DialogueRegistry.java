package com.dimalab.storymodengine.common.dialogue.registry;

import com.dimalab.storymodengine.common.dialogue.DialogueDefinition;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A simple {@code id → DialogueDefinition} lookup — mirrors {@code flow.registry.FlowRegistry}/
 * {@code cinematic.registry.CutsceneRegistry} exactly. Populated by {@code @AutoDialogue} discovery
 * or by hand; the client resolves the same definition by the same id (loaded identically on both
 * sides via {@code @AutoDialogue}/JSON) to know what a {@code DialogueStepPacket}'s node/entry index
 * actually refers to, without the definition's text ever crossing the network.
 */
public final class DialogueRegistry {

    private static final Map<ResourceLocation, DialogueDefinition> DIALOGUES = new ConcurrentHashMap<>();

    private DialogueRegistry() {
    }

    public static void register(DialogueDefinition definition) {
        DIALOGUES.put(definition.id(), definition);
    }

    public static DialogueDefinition get(ResourceLocation id) {
        return DIALOGUES.get(id);
    }
}
