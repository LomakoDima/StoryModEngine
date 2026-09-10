package com.dimalab.storymodengine.common.scripting.compiler;

import com.dimalab.storymodengine.common.quest.Quest;
import com.dimalab.storymodengine.common.quest.QuestDefinition;
import com.dimalab.storymodengine.common.scripting.ast.ObjectiveNode;
import com.dimalab.storymodengine.common.scripting.ast.QuestDeclNode;
import com.dimalab.storymodengine.common.scripting.ast.RewardNode;
import com.dimalab.storymodengine.common.scripting.registry.ScriptingRegistryFacade;

/**
 * {@code quest <id> { ... }} → {@code Quest.define(id)}'s builder — named {@code SmeQuestCompiler}
 * (not {@code QuestCompiler}) specifically to avoid a same-simple-name collision with the existing
 * {@code common.quest.QuestCompiler} (which compiles an already-built {@code QuestDefinition} into a
 * {@code Flow} — a completely different job from this class, which builds the {@code QuestDefinition}
 * itself from AST).
 */
public final class SmeQuestCompiler {

    private SmeQuestCompiler() {
    }

    public static QuestDefinition compile(QuestDeclNode node) {
        QuestDefinition.Builder builder = Quest.define(ScriptingRegistryFacade.storyId(node.id()));
        if (node.title() != null) {
            builder.title(node.title());
        }
        if (node.description() != null) {
            builder.description(node.description());
        }
        for (String prereq : node.prerequisites()) {
            builder.prerequisite(ScriptingRegistryFacade.storyId(prereq));
        }
        for (ObjectiveNode obj : node.objectives()) {
            builder.objective(ObjectiveDispatch.compile(obj));
        }
        for (RewardNode reward : node.rewards()) {
            builder.reward(RewardDispatch.compile(reward));
        }
        return builder.build();
    }
}
