package com.dimalab.storymodengine.common.scripting.registry;

import com.dimalab.storymodengine.common.cinematic.CutsceneDefinition;
import com.dimalab.storymodengine.common.cinematic.registry.CutsceneRegistry;
import com.dimalab.storymodengine.common.dialogue.DialogueDefinition;
import com.dimalab.storymodengine.common.dialogue.registry.DialogueRegistry;
import com.dimalab.storymodengine.common.flow.Flow;
import com.dimalab.storymodengine.common.flow.registry.FlowRegistry;
import com.dimalab.storymodengine.common.quest.QuestDefinition;
import com.dimalab.storymodengine.common.quest.QuestRegistry;
import com.dimalab.storymodengine.common.trigger.Trigger;
import com.dimalab.storymodengine.common.trigger.TriggerRegistry;
import net.minecraft.resources.ResourceLocation;

/**
 * The one lookup path shared by validation ({@code ReferenceResolutionPass}) and compilation (every
 * {@code *Compiler}) for a cross-reference to a dialogue/quest/cutscene/trigger/sequence — every
 * {@code *Registry.get} underneath is verified to return {@code null} on a missing id, never throw,
 * matching how {@code DialogueCommand.playCutscene} etc. already null-check.
 *
 * <p>Also owns the two id-namespacing conventions this subsystem needs: an SME-authored id (a
 * dialogue/quest/trigger/sequence declared in a {@code .sme} file) defaults to the {@code
 * storymodengine} namespace when unqualified, exactly matching {@code Trigger.Builder}'s own
 * existing convention (verified in {@code common.trigger.Trigger}); a *content* id (an item/entity/
 * block id passed to a built-in command or objective) defaults to {@code minecraft} instead, since
 * those almost always name vanilla registry entries.
 */
public final class ScriptingRegistryFacade {

    private ScriptingRegistryFacade() {
    }

    public static ResourceLocation storyId(String raw) {
        return withDefaultNamespace(raw, "storymodengine");
    }

    public static ResourceLocation contentId(String raw) {
        return withDefaultNamespace(raw, "minecraft");
    }

    private static ResourceLocation withDefaultNamespace(String raw, String defaultNamespace) {
        return raw.contains(":") ? new ResourceLocation(raw) : new ResourceLocation(defaultNamespace, raw);
    }

    public static DialogueDefinition dialogue(String id) {
        return DialogueRegistry.get(storyId(id));
    }

    public static QuestDefinition quest(String id) {
        return QuestRegistry.get(storyId(id));
    }

    public static CutsceneDefinition cutscene(String id) {
        return CutsceneRegistry.get(storyId(id));
    }

    public static Trigger trigger(String id) {
        return TriggerRegistry.get(storyId(id));
    }

    public static Flow sequence(String id) {
        return FlowRegistry.get(storyId(id));
    }
}
