package com.dimalab.storymodengine.common.dialogue;

import com.dimalab.storymodengine.common.dialogue.event.DialogueCancelledEvent;
import com.dimalab.storymodengine.common.dialogue.event.DialogueChoiceSelectedEvent;
import com.dimalab.storymodengine.common.dialogue.event.DialogueCompletedEvent;
import com.dimalab.storymodengine.common.dialogue.event.DialogueLineCompletedEvent;
import com.dimalab.storymodengine.common.dialogue.event.DialogueStartedEvent;
import com.dimalab.storymodengine.common.dialogue.network.DialogueStepPacket;
import com.dimalab.storymodengine.common.dialogue.network.StopDialoguePacket;
import com.dimalab.storymodengine.common.event.Events;
import com.dimalab.storymodengine.common.flow.Flow;
import com.dimalab.storymodengine.common.flow.FlowHandle;
import com.dimalab.storymodengine.common.flow.FlowManager;
import com.dimalab.storymodengine.common.flow.registry.FlowRegistry;
import com.dimalab.storymodengine.common.logging.EngineLog;
import com.dimalab.storymodengine.common.network.Network;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The public entry point to this whole system — everything else ({@link DialogueRunner}, {@link
 * DialogueInstance}, the compiled {@code Flow}) is reached through here. One dialogue at a time per
 * player (starting a new one cancels whatever's running, the same policy {@code CinematicManager}
 * already uses for cutscenes/title cards). Two players starting the *same* {@link
 * DialogueDefinition} get two fully independent {@link DialogueInstance}s for free: the compiled
 * {@code Flow} is cached once per definition and registered once in the *existing* {@link
 * FlowRegistry}; {@link FlowManager#start} already gives every caller its own private {@code Node}
 * tree from one shared {@code Flow} value.
 *
 * <p>Deliberately has no {@code tick()}/{@code advance()} of its own — {@code
 * flow.FlowManager}'s existing tick already drives every dialogue step (see {@link DialogueRunner}
 * for how a step compiles to ordinary {@code Flow} nodes); adding a second driver here would be
 * exactly the second execution engine this whole package exists to avoid.
 */
public final class DialogueSystem {

    private static final Map<ResourceLocation, Flow> COMPILED = new ConcurrentHashMap<>();
    private static final Map<UUID, DialogueInstance> ACTIVE = new ConcurrentHashMap<>();

    private DialogueSystem() {
    }

    /** Starts {@code definition} for {@code player}, cancelling any dialogue already running for them. */
    public static DialogueHandle start(ServerPlayer player, DialogueDefinition definition) {
        stop(player);

        Flow flow = COMPILED.computeIfAbsent(definition.id(), id -> DialogueRunner.compile(definition));
        ResourceLocation flowId = compiledFlowId(definition.id());
        FlowRegistry.register(flowId, flow);

        DialogueInstance instance = new DialogueInstance(player, definition.id(), definition);
        ACTIVE.put(player.getUUID(), instance);
        Events.post(new DialogueStartedEvent(player, definition.id()));

        FlowHandle handle = FlowManager.start(flowId, player);
        instance.attachHandle(handle);
        if (handle == null) {
            EngineLog.channel("Dialogue").error("Failed to start dialogue {} — see console", definition.id()).toChat(player);
            ACTIVE.remove(player.getUUID(), instance);
        }
        return new DialogueHandle(instance);
    }

    /** The dialogue currently active for {@code player}, or {@code null}. */
    public static DialogueHandle getActive(ServerPlayer player) {
        DialogueInstance instance = activeInstance(player);
        return instance == null ? null : new DialogueHandle(instance);
    }

    /**
     * Resolves {@code player}'s currently-offered choice or "continue" — the one server-authoritative
     * entry point {@link com.dimalab.storymodengine.common.dialogue.network.DialogueChoicePacket} calls.
     * Rejects anything not currently offered (wrong dialogue, unknown id, or a real choice whose
     * condition failed and was never included in what was offered) before ever touching the
     * underlying {@code Flow} — a client can only ever select what the server itself sent it.
     */
    public static boolean selectChoice(ServerPlayer player, ResourceLocation dialogueId, String transitionId) {
        DialogueInstance instance = activeInstance(player);
        if (instance == null || !instance.dialogueId().equals(dialogueId)) {
            EngineLog.channel("Dialogue").warn("selectChoice: no active dialogue {} for {}", dialogueId, player.getGameProfile().getName());
            return false;
        }
        if (instance.findChoice(transitionId) == null) {
            EngineLog.channel("Dialogue").warn(
                    "selectChoice: '{}' is not currently offered for {} — rejected", transitionId, player.getGameProfile().getName());
            return false;
        }

        boolean wasLine = instance.state() == DialogueState.WAITING_FOR_INPUT;
        String nodeId = instance.currentNodeId();
        int entryIndex = instance.currentEntryIndex();
        instance.markPlaying();

        boolean selected = instance.flowHandle().selectChoice(transitionId);
        if (!selected) {
            return false;
        }
        if (wasLine) {
            Events.post(new DialogueLineCompletedEvent(player, dialogueId, nodeId, entryIndex));
        } else {
            Events.post(new DialogueChoiceSelectedEvent(player, dialogueId, transitionId));
        }
        return true;
    }

    /** Cancels {@code player}'s active dialogue, if any. A no-op if none is running. */
    public static void stop(ServerPlayer player) {
        DialogueInstance instance = activeInstance(player);
        if (instance == null) {
            return;
        }
        if (instance.flowHandle() != null) {
            instance.flowHandle().cancel();
        }
        instance.markCancelled();
    }

    /** Read by {@link DialogueRunner}'s compiled closures — the compiled {@code Flow} is shared, so it looks this up fresh every time rather than closing over one instance. */
    static DialogueInstance activeInstance(ServerPlayer player) {
        return player == null ? null : ACTIVE.get(player.getUUID());
    }

    /** Called by {@link DialogueInstance#showLine}/{@link DialogueInstance#showChoices} the moment either actually happens — see their Javadoc for why timing can't be assumed by the caller of {@link #start}/{@link #selectChoice}. */
    static void notifyStep(DialogueInstance instance) {
        List<String> choiceIds = instance.currentChoices().stream().map(DialogueChoice::id).toList();
        Network.sendToPlayer(instance.player(), new DialogueStepPacket(
                instance.dialogueId(), instance.currentNodeId(), instance.currentEntryIndex(), choiceIds));
    }

    /** Called by {@link DialogueInstance#markCompleted}/{@link DialogueInstance#markCancelled}. */
    static void notifyFinished(DialogueInstance instance, boolean cancelled) {
        ACTIVE.remove(instance.player().getUUID(), instance);
        if (cancelled) {
            Events.post(new DialogueCancelledEvent(instance.player(), instance.dialogueId()));
        } else {
            Events.post(new DialogueCompletedEvent(instance.player(), instance.dialogueId()));
        }
        Network.sendToPlayer(instance.player(), new StopDialoguePacket());
    }

    private static ResourceLocation compiledFlowId(ResourceLocation dialogueId) {
        return new ResourceLocation(dialogueId.getNamespace(), dialogueId.getPath() + "_flow");
    }
}
