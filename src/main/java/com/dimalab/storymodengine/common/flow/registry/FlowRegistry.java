package com.dimalab.storymodengine.common.flow.registry;

import com.dimalab.storymodengine.common.flow.Flow;
import com.dimalab.storymodengine.common.flow.FlowDefinition;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A simple {@code id → Flow} lookup — {@link #register} once per top-level Flow a mod author wants
 * to be startable/resumable by id (e.g. from a command, or on player join). Discoverable
 * automatically for {@code public static final FlowDefinition} fields via {@code @AutoFlow}
 * (mirroring {@code @AutoContent}), or registered by hand — both coexist, the same as every other
 * discovery system in this engine.
 */
public final class FlowRegistry {

    private static final Map<ResourceLocation, Flow> FLOWS = new ConcurrentHashMap<>();

    private FlowRegistry() {
    }

    public static void register(ResourceLocation id, Flow flow) {
        FLOWS.put(id, flow);
    }

    public static void register(FlowDefinition definition) {
        register(definition.id(), definition.graph());
    }

    public static Flow get(ResourceLocation id) {
        return FLOWS.get(id);
    }
}
