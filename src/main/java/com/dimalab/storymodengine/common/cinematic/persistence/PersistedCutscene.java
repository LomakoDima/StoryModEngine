package com.dimalab.storymodengine.common.cinematic.persistence;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/**
 * Just enough to restore a running cutscene after a relog — the definition's own id (resolved back
 * through {@code CutsceneRegistry} on reconnect, so only a registered/{@code @AutoCutscene}
 * definition is restorable — a dynamically-built one, like the example gallery's, has nothing to
 * look up and simply isn't persisted, documented rather than silently broken), the tick it was at,
 * and the actor bindings it was started with. A plain record — every component already resolves
 * through the existing {@code SerializerRegistry}/{@code PacketSerializer}, so this needs no custom
 * serializer of its own (unlike {@code flow.FlowState}, whose shape has changed once before).
 */
public record PersistedCutscene(ResourceLocation definitionId, int tick, Map<String, Integer> actorEntityIds) {
}
