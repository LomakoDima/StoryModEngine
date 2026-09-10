package com.dimalab.storymodengine.common.scripting.compiler;

import com.dimalab.storymodengine.common.dialogue.registry.DialogueRegistry;
import com.dimalab.storymodengine.common.entity.npc.NpcDefinitionRegistry;
import com.dimalab.storymodengine.common.flow.registry.FlowRegistry;
import com.dimalab.storymodengine.common.logging.EngineLog;
import com.dimalab.storymodengine.common.quest.QuestRegistry;
import com.dimalab.storymodengine.common.scripting.ast.*;
import com.dimalab.storymodengine.common.scripting.registry.ScriptingRegistryFacade;
import com.dimalab.storymodengine.common.shop.ShopDefinitionRegistry;
import com.dimalab.storymodengine.common.trigger.Trigger;
import com.dimalab.storymodengine.common.trigger.TriggerRegistry;
import com.dimalab.storymodengine.common.trigger.TriggerSystem;

import java.util.Map;

/**
 * The top-level orchestrator: {@link StoryNode} → registered content, exactly what hand-written
 * {@code @Auto*}-discovered Java would produce for the same content, going straight into the same
 * registries. {@code var} declarations need no runtime action (see {@code
 * persistence.StoryVariableStore}'s class doc for the documented MVP simplification); {@code
 * include} is parsed but not yet compiled (see the design doc's non-goals section) — reported once
 * per occurrence rather than silently dropped.
 */
public final class SmeCompiler {

    private SmeCompiler() {
    }

    public static void compile(String file, StoryNode story, Map<String, SmeValueType> varTypes) {
        CompileContext ctx = new CompileContext(file, story, varTypes);
        DialogueCompiler dialogueCompiler = new DialogueCompiler(ctx);

        for (SmeNode decl : story.declarations()) {
            if (decl instanceof VarDeclNode) {
                // Establishes the variable's type for validation only — no runtime action needed.
            } else if (decl instanceof DialogueDeclNode d) {
                DialogueRegistry.register(dialogueCompiler.compile(d));
            } else if (decl instanceof QuestDeclNode q) {
                QuestRegistry.register(SmeQuestCompiler.compile(q));
            } else if (decl instanceof TriggerDeclNode t) {
                Trigger trigger = TriggerCompiler.compile(t, ctx);
                TriggerRegistry.register(trigger);
                TriggerSystem.arm(trigger);
            } else if (decl instanceof SequenceDeclNode s && s.id() != null) {
                var flow = new SequenceCompiler(ctx).compile(s.body());
                FlowRegistry.register(ScriptingRegistryFacade.storyId(s.id()), flow);
            } else if (decl instanceof NpcDeclNode n) {
                NpcDefinitionRegistry.register(SmeNpcCompiler.compile(n, ctx));
            } else if (decl instanceof ShopDeclNode s) {
                ShopDefinitionRegistry.register(SmeShopCompiler.compile(s));
            } else if (decl instanceof IncludeNode inc) {
                EngineLog.channel("SME").warn("[{}] 'include \"{}\"' is not yet implemented — declaration ignored", file, inc.relativeFilePath());
            }
        }
    }
}
