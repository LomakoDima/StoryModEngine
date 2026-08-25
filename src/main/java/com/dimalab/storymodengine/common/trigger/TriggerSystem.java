package com.dimalab.storymodengine.common.trigger;

import com.dimalab.storymodengine.api.event.Event;
import com.dimalab.storymodengine.api.event.EventPriority;
import com.dimalab.storymodengine.common.event.Events;
import com.dimalab.storymodengine.common.flow.FlowManager;
import com.dimalab.storymodengine.common.flow.registry.FlowRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The runtime — arms a {@link Trigger}'s detector and, once it fires, starts the trigger's {@code
 * Flow} through the existing {@code FlowManager}. No Trigger-specific execution engine exists here:
 * {@link #fireForPlayer}/{@link #fireGlobal} do nothing but an eligibility check followed by one
 * {@code FlowManager.start(flowId, player)} call, the same "cache the Flow under a derived id,
 * re-register into {@code FlowRegistry} on every use" pattern {@code QuestSystem} already
 * established for its own compiled flows.
 *
 * <p>{@code LOCATION}/{@code TIME} triggers need no explicit arming: {@code
 * integration.TriggerTickBridge} walks {@link TriggerRegistry#all()} itself every poll, filtering by
 * {@link TriggerKind}, so there's no separate "armed list" to keep in sync with the registry. Only
 * {@code EVENT} triggers need a one-time {@link #arm} call, to subscribe on the existing {@code
 * EventBus}.
 */
public final class TriggerSystem {

    private static final Map<ResourceLocation, ResourceLocation> COMPILED_FLOW_IDS = new ConcurrentHashMap<>();

    private TriggerSystem() {
    }

    /** Called once per discovered/registered trigger. A no-op for {@code LOCATION}/{@code TIME} — see the class doc. */
    public static void arm(Trigger trigger) {
        if (trigger.kind() != TriggerKind.EVENT) {
            return;
        }
        armEvent(trigger);
    }

    @SuppressWarnings("unchecked")
    private static void armEvent(Trigger trigger) {
        Events.bus().subscribe((Class<Event>) trigger.eventType(), EventPriority.NORMAL, event -> onEventFired(trigger, event));
    }

    private static void onEventFired(Trigger trigger, Event event) {
        if (!trigger.enabled()) {
            return;
        }
        ServerPlayer player = trigger.playerExtractor() != null ? trigger.playerExtractor().apply(event) : null;
        if (player != null) {
            fireForPlayer(trigger, player);
        } else {
            fireGlobal(trigger);
        }
    }

    /** Fires for one specific player — {@code LOCATION}, per-player {@code EVENT}, and manual {@code /trigger fire} all route through here. */
    public static void fireForPlayer(Trigger trigger, ServerPlayer player) {
        if (!trigger.enabled()) {
            return;
        }
        if (trigger.policy() == TriggerPolicy.ONCE && TriggerFiredTracker.hasFired(trigger, player)) {
            return;
        }
        TriggerFiredTracker.markFired(trigger, player);
        FlowManager.start(compiledFlowId(trigger), player);
    }

    /** Fires independently for every currently-online player — {@code TIME} triggers, and any {@code EVENT} trigger whose player couldn't be identified. */
    public static void fireGlobal(Trigger trigger) {
        if (!trigger.enabled()) {
            return;
        }
        if (trigger.policy() == TriggerPolicy.ONCE && TriggerFiredTracker.hasFiredGlobal(trigger)) {
            return;
        }
        TriggerFiredTracker.markFiredGlobal(trigger);
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        ResourceLocation flowId = compiledFlowId(trigger);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            FlowManager.start(flowId, player);
        }
    }

    private static ResourceLocation compiledFlowId(Trigger trigger) {
        return COMPILED_FLOW_IDS.computeIfAbsent(trigger.id(), id -> {
            ResourceLocation flowId = new ResourceLocation(id.getNamespace(), id.getPath() + "_trigger_flow");
            FlowRegistry.register(flowId, trigger.flow());
            return flowId;
        });
    }
}
