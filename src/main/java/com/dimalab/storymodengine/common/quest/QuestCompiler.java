package com.dimalab.storymodengine.common.quest;

import com.dimalab.storymodengine.api.quest.QuestState;
import com.dimalab.storymodengine.api.quest.QuestStep;
import com.dimalab.storymodengine.common.event.Events;
import com.dimalab.storymodengine.common.flow.Flow;
import com.dimalab.storymodengine.common.quest.event.QuestCompletedEvent;
import com.dimalab.storymodengine.common.quest.persistence.QuestProgressStore;
import com.dimalab.storymodengine.api.quest.reward.Reward;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link QuestDefinition} → {@link Flow} — the entire "orchestration layer" this task asks for, in
 * one method. Every step ({@code quest.objective.Objective}s and {@link BranchStep}s alike) already
 * knows how to compile itself (see {@link QuestStep}); this class only concatenates them plus the
 * completion/reward tail, via {@code Flow.sequence} — never inspects *which* kind of step or reward
 * it has. {@code QUEST_STARTED}/initial progress registration deliberately happen in {@code
 * QuestSystem.start} instead, not here — the same split {@code DialogueSystem.start} already uses
 * for {@code DialogueStartedEvent} (a synchronous fact about calling {@code start}, not something
 * the Flow itself needs to know about).
 */
public final class QuestCompiler {

    private QuestCompiler() {
    }

    public static Flow compile(QuestDefinition quest) {
        List<Flow> flowSteps = new ArrayList<>();
        for (QuestStep step : quest.steps()) {
            flowSteps.add(step.compile(quest.id()));
        }
        flowSteps.add(Flow.action(ctx -> QuestProgressStore.setQuestState(ctx.player(), quest.id(), QuestState.COMPLETED)));
        flowSteps.add(Flow.action(ctx -> Events.post(new QuestCompletedEvent(ctx.player(), quest.id()))));
        for (Reward reward : quest.rewards()) {
            flowSteps.add(reward.compile());
        }
        flowSteps.add(Flow.action(ctx -> QuestSystem.notifyCompleted(ctx.player(), quest.id())));
        return Flow.sequence(flowSteps.toArray(new Flow[0]));
    }
}
