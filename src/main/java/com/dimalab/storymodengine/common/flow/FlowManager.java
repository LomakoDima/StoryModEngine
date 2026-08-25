package com.dimalab.storymodengine.common.flow;

import com.dimalab.storymodengine.api.flow.FlowRunState;
import com.dimalab.storymodengine.common.capabilities.Capabilities;
import com.dimalab.storymodengine.common.event.Events;
import com.dimalab.storymodengine.common.flow.integration.event.FlowCompletedEvent;
import com.dimalab.storymodengine.common.flow.integration.event.FlowFailedEvent;
import com.dimalab.storymodengine.common.flow.integration.persistence.ModFlowCapabilities;
import com.dimalab.storymodengine.common.flow.integration.persistence.StoryFlowData;
import com.dimalab.storymodengine.common.flow.registry.FlowRegistry;
import com.dimalab.storymodengine.common.logging.EngineLog;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Tracks every player's active {@link FlowInstance}s and drives them — the only class that touches
 * more than one player's flows at once. Several flows can be active for one player simultaneously
 * (e.g. a main story flow and a side flow, each independently ticked and persisted under its own
 * {@code flowId}), and several players can run the same {@link Flow} definition independently
 * (each with their own {@link FlowInstance}, per {@link Flow}'s own Javadoc).
 *
 * <p>Persistence and network sync are both the existing {@code capabilities} system, not a second
 * one: every meaningful transition (start, node change, pause/resume, completion, failure,
 * cancellation) updates {@code StoryFlowData#activeFlows} in memory (cheap — a plain map write) and
 * calls the existing {@code ModFlowCapabilities.FLOW_DATA.markDirty}/{@code .sync} — never on every
 * tick, the same "explicit, not automatic" dirty discipline {@code capabilities} already
 * established. Actual NBT persistence needs no extra call at all: {@code Entity} already serializes
 * {@code ForgeCaps} automatically on save, and {@code StoryFlowData} is just one more {@code
 * @Capability}.
 *
 * <p><b>Threading</b>: {@link #tick()} runs synchronously on whichever thread calls it — in
 * practice the server thread, via {@code flow.integration.FlowTickBridge}. "Parallel" nodes execute
 * concurrently within one call to {@link #tick()}, never on separate OS threads — see {@code
 * Parallel}'s Javadoc.
 */
public final class FlowManager {

    private static final Map<UUID, Map<ResourceLocation, FlowInstance>> ACTIVE = new ConcurrentHashMap<>();

    private FlowManager() {
    }

    /** Starts a fresh run of the Flow registered under {@code flowId} for {@code player}. */
    public static FlowHandle start(ResourceLocation flowId, ServerPlayer player) {
        Flow flow = FlowRegistry.get(flowId);
        if (flow == null) {
            EngineLog.channel("Flow").warn("start({}) — no Flow registered under this id", flowId);
            return null;
        }
        FlowContext context = new FlowContext(player, flowId);
        FlowRuntime runtime = FlowRuntime.start(flowId, flow, context);
        FlowInstance instance = new FlowInstance(flowId, runtime, FlowRunState.RUNNING);
        instancesFor(player).put(flowId, instance);
        persist(player, instance);
        return new FlowHandle(instance);
    }

    /** Advances every active, non-paused flow, for every player, by one step — called once per server tick by {@code FlowTickBridge}. */
    public static void tick() {
        for (Map<ResourceLocation, FlowInstance> playerFlows : ACTIVE.values()) {
            for (FlowInstance instance : playerFlows.values().toArray(FlowInstance[]::new)) {
                if (instance.runState() != FlowRunState.RUNNING) {
                    continue;
                }
                var before = instance.runtime().state();
                instance.runtime().tick();
                if (instance.runtime().state() != before) {
                    instance.syncFromNodeState();
                    onTransition(instance);
                }
            }
        }
    }

    /**
     * Cancels {@code player}'s running/paused instance of {@code flowId}, if any — the single entry
     * point for an externally-triggered cancel ({@link FlowHandle#cancel()}). {@link
     * FlowInstance#cancel()} alone only flips the instance's own {@code runState}; calling it
     * directly (the previous behavior) skipped {@link #onTransition}, so the cancelled instance was
     * never unregistered from {@link #ACTIVE}, never removed from {@code StoryFlowData.activeFlows},
     * and no completion-side bookkeeping ran — it just sat forever as {@code CANCELLED}. Routing
     * through here instead mirrors exactly what {@link #tick()} does for a node-driven transition:
     * detect the before/after change, then run {@link #onTransition} once.
     */
    static void cancel(ServerPlayer player, ResourceLocation flowId) {
        FlowInstance instance = instancesFor(player).get(flowId);
        if (instance == null) {
            return;
        }
        FlowRunState before = instance.runState();
        if (before != FlowRunState.RUNNING && before != FlowRunState.PAUSED) {
            return;
        }
        instance.cancel();
        onTransition(instance);
    }

    /** Resolves {@code player}'s currently-awaiting {@code Choice} on {@code flowId}, if any. */
    public static boolean selectChoice(ServerPlayer player, ResourceLocation flowId, String transitionId) {
        FlowInstance instance = instancesFor(player).get(flowId);
        if (instance == null) {
            EngineLog.channel("Flow").warn("selectChoice: no active flow {} for {}", flowId, player.getGameProfile().getName());
            return false;
        }
        boolean selected = instance.selectChoice(transitionId);
        if (selected) {
            persist(player, instance);
        }
        return selected;
    }

    public static Collection<FlowHandle> getActiveFlows(ServerPlayer player) {
        return instancesFor(player).values().stream().map(FlowHandle::new).collect(Collectors.toList());
    }

    /** Restores every resumable ({@code RUNNING} or {@code PAUSED}) flow saved in {@code player}'s {@code StoryFlowData} — called on {@code PlayerConnectedEvent}. */
    public static void resumeAll(ServerPlayer player) {
        StoryFlowData data = Capabilities.get(player, ModFlowCapabilities.FLOW_DATA);
        if (data == null || data.activeFlows.isEmpty()) {
            return;
        }
        int resumed = 0;
        for (FlowState state : data.activeFlows.values()) {
            if (state.runState != FlowRunState.RUNNING && state.runState != FlowRunState.PAUSED) {
                continue;
            }
            Flow flow = FlowRegistry.get(state.flowId);
            if (flow == null) {
                EngineLog.channel("Flow").warn("resumeAll: {} has no Flow registered under {}, dropping", player.getGameProfile().getName(), state.flowId);
                continue;
            }
            FlowContext context = new FlowContext(player, state.flowId);
            FlowRuntime runtime = FlowRuntime.resume(state.flowId, flow, context, state);
            FlowInstance instance = new FlowInstance(state.flowId, runtime, state.runState);
            instancesFor(player).put(state.flowId, instance);
            resumed++;
        }
        if (resumed > 0) {
            EngineLog.channel("Flow").info("Restored {} flow(s) for {}", resumed, player.getGameProfile().getName());
        }
    }

    private static Map<ResourceLocation, FlowInstance> instancesFor(ServerPlayer player) {
        return ACTIVE.computeIfAbsent(player.getUUID(), id -> new ConcurrentHashMap<>());
    }

    private static void onTransition(FlowInstance instance) {
        ServerPlayer player = instance.runtime().player();
        persist(player, instance);
        switch (instance.runState()) {
            case COMPLETED -> {
                EngineLog.channel("Flow").info("Flow {} completed for {}", instance.flowId(), player.getGameProfile().getName());
                Events.post(new FlowCompletedEvent(player, instance.flowId()));
                instancesFor(player).remove(instance.flowId());
            }
            case FAILED -> {
                EngineLog.channel("Flow").warn("Flow {} failed for {}", instance.flowId(), player.getGameProfile().getName());
                Events.post(new FlowFailedEvent(player, instance.flowId()));
                instancesFor(player).remove(instance.flowId());
            }
            case CANCELLED -> {
                EngineLog.channel("Flow").info("Flow {} cancelled for {}", instance.flowId(), player.getGameProfile().getName());
                instancesFor(player).remove(instance.flowId());
            }
            default -> {
                // still RUNNING (e.g. a Sequence moved to its next node, or a Choice was resolved) — just persisted above
            }
        }
    }

    private static void persist(ServerPlayer player, FlowInstance instance) {
        StoryFlowData data = Capabilities.get(player, ModFlowCapabilities.FLOW_DATA);
        if (data == null) {
            return;
        }
        FlowRunState runState = instance.runState();
        if (runState == FlowRunState.COMPLETED || runState == FlowRunState.FAILED || runState == FlowRunState.CANCELLED) {
            data.activeFlows.remove(instance.flowId().toString());
        } else {
            data.activeFlows.put(instance.flowId().toString(), instance.toFlowState());
        }
        Capabilities.markDirty(player, ModFlowCapabilities.FLOW_DATA);
        ModFlowCapabilities.FLOW_DATA.sync(player);
    }
}
