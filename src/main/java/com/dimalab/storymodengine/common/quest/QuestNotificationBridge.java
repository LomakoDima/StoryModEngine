package com.dimalab.storymodengine.common.quest;

import com.dimalab.storymodengine.api.quest.ObjectiveState;
import com.dimalab.storymodengine.api.event.SubscribeEvent;
import com.dimalab.storymodengine.common.network.Network;
import com.dimalab.storymodengine.common.quest.event.QuestCompletedEvent;
import com.dimalab.storymodengine.common.quest.event.QuestFailedEvent;
import com.dimalab.storymodengine.common.quest.event.QuestObjectiveProgressedEvent;
import com.dimalab.storymodengine.common.quest.event.QuestStartedEvent;
import com.dimalab.storymodengine.common.quest.network.QuestToastPacket;
import com.dimalab.storymodengine.common.quest.objective.Objective;

/**
 * Server-side only: translates the four {@code quest.event} classes into a short player-facing
 * string and sends {@link QuestToastPacket} — see the design doc §8's note on why this one packet
 * exists (capability sync covers state, never a one-shot "this just happened" notification). Static
 * {@code @SubscribeEvent} methods, auto-discovered by {@code event.discovery.EventListenerDiscovery}
 * the same way every other listener in this engine is.
 *
 * <p>Only {@code ACTIVE}/{@code COMPLETED} objective transitions toast (not every progress
 * increment) — continuous progress numbers are what {@code QuestTrackerOverlay} is for, not chat
 * spam from e.g. a kill-20 objective.
 */
public final class QuestNotificationBridge {

    private QuestNotificationBridge() {
    }

    @SubscribeEvent
    public static void onQuestStarted(QuestStartedEvent event) {
        QuestDefinition quest = QuestRegistry.get(event.questId());
        String title = quest != null ? quest.title() : event.questId().toString();
        Network.sendToPlayer(event.player(), new QuestToastPacket("Started: " + title));
    }

    @SubscribeEvent
    public static void onQuestCompleted(QuestCompletedEvent event) {
        QuestDefinition quest = QuestRegistry.get(event.questId());
        String title = quest != null ? quest.title() : event.questId().toString();
        Network.sendToPlayer(event.player(), new QuestToastPacket("Completed: " + title));
    }

    @SubscribeEvent
    public static void onQuestFailed(QuestFailedEvent event) {
        QuestDefinition quest = QuestRegistry.get(event.questId());
        String title = quest != null ? quest.title() : event.questId().toString();
        Network.sendToPlayer(event.player(), new QuestToastPacket("Failed: " + title));
    }

    @SubscribeEvent
    public static void onObjectiveProgressed(QuestObjectiveProgressedEvent event) {
        if (event.state() != ObjectiveState.ACTIVE && event.state() != ObjectiveState.COMPLETED) {
            return;
        }
        String label = objectiveDescription(event);
        String text = event.state() == ObjectiveState.COMPLETED
                ? "Objective complete: " + label
                : "New objective: " + label;
        Network.sendToPlayer(event.player(), new QuestToastPacket(text));
    }

    private static String objectiveDescription(QuestObjectiveProgressedEvent event) {
        QuestDefinition quest = QuestRegistry.get(event.questId());
        if (quest != null) {
            for (Objective objective : quest.objectives()) {
                if (objective.id().equals(event.objectiveId())) {
                    return objective.description();
                }
            }
        }
        return event.objectiveId();
    }
}
