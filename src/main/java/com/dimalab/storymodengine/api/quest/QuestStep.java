package com.dimalab.storymodengine.api.quest;

import com.dimalab.storymodengine.common.flow.Flow;
import net.minecraft.resources.ResourceLocation;

/**
 * One entry in a quest's compiled sequence — {@code quest.objective.Objective} extends this
 * (an objective *is* a step); {@link BranchStep} is the other implementation, for §8's branching.
 * Public (not package-private, despite only {@code QuestCompiler}/{@code QuestDefinition} needing
 * it directly) purely so {@code Objective}, which lives in the sibling {@code quest.objective}
 * package, can implement it.
 */
public interface QuestStep {

    Flow compile(ResourceLocation questId);
}
