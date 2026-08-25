package com.dimalab.storymodengine.common.flow;

import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Flow-scoped variables — explicitly not a persistence system. The line the task draws: {@code
 * Capabilities} owns persistent Minecraft state; {@code Blackboard} owns transient Flow execution
 * state. Nothing here is ever written to NBT — a node that needs a value to survive a save/reload
 * stores it through {@code Capabilities} instead (the required example already does exactly this
 * for Story Points), the same boundary {@link FlowContext}'s pre-existing scratch map already drew
 * before this class formalized it into {@link Scope}s.
 *
 * <p>{@link Scope#GLOBAL} and {@link Scope#FLOW} are process-lifetime, shared storage (one map for
 * the whole JVM, one per {@link FlowDefinition} id) — genuinely shared, not per-instance.
 * {@link Scope#INSTANCE} is this Blackboard's own private map (one per {@link FlowInstance}).
 * {@link Scope#NODE} is a separate map, but — deliberately, to avoid overengineering a scope
 * nothing here needs yet — does *not* automatically namespace by the calling node's own path (no
 * node lifecycle method threads its own path down to {@link FlowContext}); callers should use keys
 * that are unique enough on their own until that's worth building.
 */
public final class Blackboard {

    private static final Map<String, Object> GLOBAL = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, Map<String, Object>> FLOW = new ConcurrentHashMap<>();

    private final ResourceLocation flowId;
    private final Map<String, Object> instance = new HashMap<>();
    private final Map<String, Object> node = new HashMap<>();

    public Blackboard(ResourceLocation flowId) {
        this.flowId = flowId;
    }

    @SuppressWarnings("unchecked")
    public <T> T get(Scope scope, String key) {
        return (T) mapFor(scope).get(key);
    }

    public <T> void set(Scope scope, String key, T value) {
        mapFor(scope).put(key, value);
    }

    private Map<String, Object> mapFor(Scope scope) {
        return switch (scope) {
            case GLOBAL -> GLOBAL;
            case FLOW -> FLOW.computeIfAbsent(flowId, id -> new ConcurrentHashMap<>());
            case INSTANCE -> instance;
            case NODE -> node;
        };
    }
}
