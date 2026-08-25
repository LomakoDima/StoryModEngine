package com.dimalab.storymodengine.common.quest.objective;

import com.dimalab.storymodengine.common.flow.Flow;
import com.dimalab.storymodengine.common.quest.event.QuestCompletedEvent;
import net.minecraft.resources.ResourceLocation;

/**
 * {@code Objective.quest(otherQuestId)} — waits for another quest's {@link QuestCompletedEvent},
 * without starting it (that quest is expected to be started independently — see {@code
 * Reward.unlockQuest} for the forward-chaining half of this, design doc §7). Lets a quest's own
 * objective list depend on a *different, still-running* quest finishing mid-flight, distinct from
 * {@code QuestDefinition.Builder#prerequisite}, which only gates whether a quest can *start* at all.
 */
final class QuestCompletionObjective implements Objective {

    private final String id;
    private final ResourceLocation targetQuestId;

    QuestCompletionObjective(ResourceLocation targetQuestId) {
        this.id = "quest_" + ObjectiveSupport.sanitize(targetQuestId);
        this.targetQuestId = targetQuestId;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String description() {
        return "Complete " + targetQuestId;
    }

    @Override
    public Flow compile(ResourceLocation questId) {
        return ObjectiveSupport.waitFor(questId, this, QuestCompletedEvent.class,
                (player, event) -> event.player().equals(player) && event.questId().equals(targetQuestId));
    }
}
