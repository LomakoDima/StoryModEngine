package com.dimalab.storymodengine.common.scripting.compiler;

import com.dimalab.storymodengine.common.quest.objective.Objective;
import com.dimalab.storymodengine.common.scripting.ast.ObjectiveNode;
import com.dimalab.storymodengine.common.scripting.registry.ScriptingRegistryFacade;
import net.minecraft.resources.ResourceLocation;

/**
 * {@code objective <kind> <target> [count]} → {@code Objective.kill/collect/talkTo/interact/
 * findEntity/dialogue/quest(...)} — a closed dispatch table, not a switch inside {@code
 * SmeQuestCompiler} itself, matching {@code Objective}'s own polymorphic-dispatch discipline. {@code
 * location} is not supported (needs a coordinate literal this grammar deliberately doesn't have —
 * see {@code TriggerCompiler}'s identical zone-literal boundary).
 */
public final class ObjectiveDispatch {

    private ObjectiveDispatch() {
    }

    public static Objective compile(ObjectiveNode node) {
        return switch (node.kind()) {
            case "kill" -> Objective.kill(contentId(node), requireCount(node));
            case "collect" -> Objective.collect(contentId(node), requireCount(node));
            case "talk_to" -> Objective.talkTo(contentId(node));
            case "interact" -> Objective.interact(contentId(node));
            case "find_entity" -> Objective.findEntity(contentId(node), (double) requireCount(node));
            case "dialogue" -> Objective.dialogue(storyId(node));
            case "quest" -> Objective.quest(storyId(node));
            default -> throw new IllegalStateException("Unknown objective kind '" + node.kind() + "' (should have failed validation)");
        };
    }

    private static ResourceLocation contentId(ObjectiveNode node) {
        return ScriptingRegistryFacade.contentId(node.targetId());
    }

    private static ResourceLocation storyId(ObjectiveNode node) {
        return ScriptingRegistryFacade.storyId(node.targetId());
    }

    private static int requireCount(ObjectiveNode node) {
        return node.count() != null ? node.count() : 1;
    }
}
