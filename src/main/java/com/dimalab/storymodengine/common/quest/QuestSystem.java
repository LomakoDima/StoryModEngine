package com.dimalab.storymodengine.common.quest;

import com.dimalab.storymodengine.api.quest.QuestState;
import com.dimalab.storymodengine.common.event.Events;
import com.dimalab.storymodengine.common.flow.Flow;
import com.dimalab.storymodengine.common.flow.FlowHandle;
import com.dimalab.storymodengine.common.flow.FlowManager;
import com.dimalab.storymodengine.common.flow.registry.FlowRegistry;
import com.dimalab.storymodengine.common.logging.EngineLog;
import com.dimalab.storymodengine.common.quest.event.QuestFailedEvent;
import com.dimalab.storymodengine.common.quest.event.QuestStartedEvent;
import com.dimalab.storymodengine.common.quest.persistence.QuestProgressStore;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The public runtime facade — every quest-start path in the task (§14: player action, event,
 * command, prerequisite completion, another quest, automatic) funnels through {@link #start}, which
 * does nothing but validate, compile-and-cache a {@link Flow} (never a second runtime), and hand it
 * to {@link FlowManager}. Same "cache the compiled Flow per-definition-id, re-register into {@code
 * FlowRegistry} on every start" pattern {@code DialogueSystem} already uses.
 *
 * <p>{@link #complete}/{@link #fail} are deliberately **not** exposed as externally-callable methods
 * (see the design doc §10) — a quest reaches {@code COMPLETED} only as the natural result of its own
 * compiled Flow satisfying every objective, never by a bare API call skipping straight past them
 * (server-authoritative in the same sense {@code DialogueSystem.selectChoice} validates against the
 * instance's own offered choices rather than trusting a raw request). {@link #stop} is the one
 * external termination path, and it produces {@code FAILED}, not {@code COMPLETED}.
 */
public final class QuestSystem {

    private static final Map<ResourceLocation, Flow> COMPILED = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<ResourceLocation, FlowHandle>> ACTIVE = new ConcurrentHashMap<>();

    private QuestSystem() {
    }

    /**
     * Starts {@code questId} for {@code player} — {@code null} if the id isn't registered, the quest
     * is already active for this player, or an unmet {@link QuestDefinition#prerequisites()} entry
     * blocks it (checked here, never compiled into the Flow — a rejected start never creates a Flow
     * instance at all, see the design doc §7).
     */
    public static QuestHandle start(ServerPlayer player, ResourceLocation questId) {
        QuestDefinition definition = QuestRegistry.get(questId);
        if (definition == null) {
            EngineLog.channel("Quest").warn("start({}): no quest registered under this id", questId);
            return null;
        }
        if (isActive(player, questId)) {
            EngineLog.channel("Quest").warn("start({}): already active for {}", questId, player.getGameProfile().getName());
            return null;
        }
        for (ResourceLocation prerequisite : definition.prerequisites()) {
            if (!QuestProgressStore.isCompleted(player, prerequisite)) {
                EngineLog.channel("Quest").warn("start({}): prerequisite '{}' not completed for {}",
                        questId, prerequisite, player.getGameProfile().getName());
                return null;
            }
        }

        ResourceLocation flowId = compiledFlowId(questId);
        Flow flow = COMPILED.computeIfAbsent(questId, id -> QuestCompiler.compile(definition));
        FlowRegistry.register(flowId, flow);

        QuestProgressStore.startQuest(player, questId);
        Events.post(new QuestStartedEvent(player, questId));

        FlowHandle handle = FlowManager.start(flowId, player);
        if (handle == null) {
            EngineLog.channel("Quest").error("start({}): FlowManager rejected the compiled quest flow", questId);
            return null;
        }
        instancesFor(player).put(questId, handle);
        EngineLog.channel("Quest").info("Quest '{}' started for {}", questId, player.getGameProfile().getName());
        return new QuestHandle(player, questId);
    }

    /**
     * Externally cancels an active quest — produces {@link com.dimalab.storymodengine.common.quest.event.QuestFailedEvent}, not completion.
     * {@code FlowHandle#cancel()} now routes through {@code FlowManager#cancel}, which runs the same
     * cleanup a {@code tick()}-driven transition would (persist, unregister from {@code
     * FlowManager}'s own active-instance map) — the gap where a direct {@code FlowInstance#cancel()}
     * call skipped that cleanup entirely has been fixed at the {@code flow} layer. Quest's own
     * bookkeeping below (removing from Quest's *own* {@code instancesFor} map, setting quest/objective
     * state, posting {@code QuestFailedEvent}) is separate quest-level state, not a workaround for
     * that flow-layer gap.
     */
    public static void stop(ServerPlayer player, ResourceLocation questId) {
        FlowHandle handle = instancesFor(player).remove(questId);
        if (handle == null) {
            return;
        }
        handle.cancel();
        QuestProgressStore.setQuestState(player, questId, QuestState.FAILED);
        Events.post(new QuestFailedEvent(player, questId));
        EngineLog.channel("Quest").info("Quest '{}' stopped for {}", questId, player.getGameProfile().getName());
    }

    public static boolean isActive(ServerPlayer player, ResourceLocation questId) {
        FlowHandle handle = instancesFor(player).get(questId);
        return handle != null && handle.isRunning();
    }

    public static boolean isCompleted(ServerPlayer player, ResourceLocation questId) {
        return QuestProgressStore.isCompleted(player, questId);
    }

    public static QuestProgress progress(ServerPlayer player, ResourceLocation questId) {
        return QuestProgressStore.get(player, questId);
    }

    /** Called from the compiled Flow's own final step ({@link QuestCompiler}) — drops the now-finished handle from {@link #ACTIVE} so it doesn't accumulate forever; correctness of {@link #isActive} never depended on this (a completed {@code FlowHandle} already reports {@code isRunning() == false} on its own), this is bookkeeping hygiene only. */
    static void notifyCompleted(ServerPlayer player, ResourceLocation questId) {
        instancesFor(player).remove(questId);
    }

    private static Map<ResourceLocation, FlowHandle> instancesFor(ServerPlayer player) {
        return ACTIVE.computeIfAbsent(player.getUUID(), id -> new ConcurrentHashMap<>());
    }

    private static ResourceLocation compiledFlowId(ResourceLocation questId) {
        return new ResourceLocation(questId.getNamespace(), questId.getPath() + "_quest_flow");
    }
}
