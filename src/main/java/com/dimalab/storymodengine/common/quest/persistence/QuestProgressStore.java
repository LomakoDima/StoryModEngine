package com.dimalab.storymodengine.common.quest.persistence;

import com.dimalab.storymodengine.common.capabilities.Capabilities;
import com.dimalab.storymodengine.common.event.Events;
import com.dimalab.storymodengine.api.quest.ObjectiveState;
import com.dimalab.storymodengine.common.quest.QuestProgress;
import com.dimalab.storymodengine.api.quest.QuestState;
import com.dimalab.storymodengine.common.quest.event.QuestObjectiveProgressedEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;

/**
 * The one place any compiled quest {@code Flow} (or the client-facing {@code QuestSystem} facade)
 * reads/writes a player's {@link QuestProgress} — every write replaces the whole record (see that
 * class's Javadoc) and goes through {@link Capabilities#markDirty}/{@code .sync(player)}, the same
 * "explicit, not automatic" dirty discipline every other capability in this engine already uses
 * (verified against {@code flow.FlowManager#persist}).
 */
public final class QuestProgressStore {

    private QuestProgressStore() {
    }

    public static QuestProgress get(ServerPlayer player, ResourceLocation questId) {
        QuestProgressData data = Capabilities.get(player, ModQuestCapabilities.PROGRESS_DATA);
        return data == null ? null : data.quests.get(questId);
    }

    public static boolean isActive(ServerPlayer player, ResourceLocation questId) {
        QuestProgress progress = get(player, questId);
        return progress != null && progress.state() == QuestState.ACTIVE;
    }

    public static boolean isCompleted(ServerPlayer player, ResourceLocation questId) {
        QuestProgress progress = get(player, questId);
        return progress != null && progress.state() == QuestState.COMPLETED;
    }

    public static void startQuest(ServerPlayer player, ResourceLocation questId) {
        replace(player, questId, QuestProgress.started());
    }

    public static void setQuestState(ServerPlayer player, ResourceLocation questId, QuestState state) {
        QuestProgress current = currentOrStarted(player, questId);
        replace(player, questId, new QuestProgress(state, current.objectiveProgress(), current.objectiveStates()));
    }

    public static void setObjectiveState(ServerPlayer player, ResourceLocation questId, String objectiveId, ObjectiveState state) {
        QuestProgress current = currentOrStarted(player, questId);
        Map<String, ObjectiveState> states = new HashMap<>(current.objectiveStates());
        states.put(objectiveId, state);
        QuestProgress updated = new QuestProgress(current.state(), current.objectiveProgress(), states);
        replace(player, questId, updated);
        int progress = updated.objectiveProgress().getOrDefault(objectiveId, 0);
        Events.post(new QuestObjectiveProgressedEvent(player, questId, objectiveId, progress, progress, state));
    }

    /** Adds {@code delta} to {@code objectiveId}'s progress, clamped to {@code max}; returns the new total. Posts {@link QuestObjectiveProgressedEvent} — what {@code QuestToast}/{@code QuestTrackerOverlay} react to. */
    public static int incrementObjective(ServerPlayer player, ResourceLocation questId, String objectiveId, int delta, int max) {
        QuestProgress current = currentOrStarted(player, questId);
        int newTotal = Math.min(max, current.objectiveProgress().getOrDefault(objectiveId, 0) + delta);
        Map<String, Integer> progressMap = new HashMap<>(current.objectiveProgress());
        progressMap.put(objectiveId, newTotal);
        QuestProgress updated = new QuestProgress(current.state(), progressMap, current.objectiveStates());
        replace(player, questId, updated);
        ObjectiveState state = updated.objectiveStates().getOrDefault(objectiveId, ObjectiveState.ACTIVE);
        Events.post(new QuestObjectiveProgressedEvent(player, questId, objectiveId, newTotal, max, state));
        return newTotal;
    }

    /** Sets progress to exactly {@code requiredCount} and state to {@code COMPLETED} in one write — what every {@code Objective}'s compiled Flow calls as its final step, so "completed" always means "progress == required," even for an objective whose completion isn't reached via {@link #incrementObjective}. */
    public static void completeObjective(ServerPlayer player, ResourceLocation questId, String objectiveId, int requiredCount) {
        QuestProgress current = currentOrStarted(player, questId);
        Map<String, Integer> progressMap = new HashMap<>(current.objectiveProgress());
        progressMap.put(objectiveId, requiredCount);
        Map<String, ObjectiveState> states = new HashMap<>(current.objectiveStates());
        states.put(objectiveId, ObjectiveState.COMPLETED);
        replace(player, questId, new QuestProgress(current.state(), progressMap, states));
        Events.post(new QuestObjectiveProgressedEvent(player, questId, objectiveId, requiredCount, requiredCount, ObjectiveState.COMPLETED));
    }

    private static QuestProgress currentOrStarted(ServerPlayer player, ResourceLocation questId) {
        QuestProgress current = get(player, questId);
        return current != null ? current : QuestProgress.started();
    }

    private static void replace(ServerPlayer player, ResourceLocation questId, QuestProgress progress) {
        QuestProgressData data = Capabilities.get(player, ModQuestCapabilities.PROGRESS_DATA);
        if (data == null) {
            return;
        }
        data.quests.put(questId, progress);
        Capabilities.markDirty(player, ModQuestCapabilities.PROGRESS_DATA);
        ModQuestCapabilities.PROGRESS_DATA.sync(player);
    }
}
