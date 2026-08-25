package com.dimalab.storymodengine.common.quest;

import com.dimalab.storymodengine.api.quest.QuestStep;
import com.dimalab.storymodengine.api.flow.Evaluator;
import com.dimalab.storymodengine.common.flow.Flow;
import com.dimalab.storymodengine.common.quest.objective.Objective;
import net.minecraft.resources.ResourceLocation;

/**
 * §8's branching — a two-line adapter onto the existing {@code Flow.branch}, not a new "Quest
 * branching engine." {@code condition} is the same {@code flow.Evaluator<Boolean>} type {@code
 * Dialogue}'s own {@code .condition(...)}/{@code .gate(...)} already use.
 */
final class BranchStep implements QuestStep {

    private final Evaluator<Boolean> condition;
    private final Objective ifTrue;
    private final Objective ifFalse;

    BranchStep(Evaluator<Boolean> condition, Objective ifTrue, Objective ifFalse) {
        this.condition = condition;
        this.ifTrue = ifTrue;
        this.ifFalse = ifFalse;
    }

    Objective ifTrue() {
        return ifTrue;
    }

    Objective ifFalse() {
        return ifFalse;
    }

    @Override
    public Flow compile(ResourceLocation questId) {
        return Flow.branch(condition, ifTrue.compile(questId), ifFalse.compile(questId));
    }
}
