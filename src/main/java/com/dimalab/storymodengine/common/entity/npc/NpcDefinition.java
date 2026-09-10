package com.dimalab.storymodengine.common.entity.npc;

import com.dimalab.storymodengine.api.entity.NpcBehavior;
import com.dimalab.storymodengine.common.scripting.ast.NpcAttributeSpec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * What {@code npc "<name>" { ... }} declared — pure data, like {@code quest.QuestDefinition}. Declaring
 * one does not spawn anything; {@code npc_spawn} (see {@code NpcScriptCommands}) does that later, by
 * {@link #name}, same "declared, then explicitly started" split as quest/dialogue.
 *
 * <p>{@link #name} is a plain {@code String}, deliberately <b>not</b> a {@link ResourceLocation} the
 * way quest/dialogue ids are: those are code-facing bare identifiers by convention, but an NPC's name
 * is a human-facing display string (the user's own example: {@code "Ванька"}) — a {@code
 * ResourceLocation}'s path charset rejects capitals, spaces, and anything outside {@code
 * [a-z0-9_.-/]}, so routing it through {@code ScriptingRegistryFacade.storyId} would throw on nearly
 * every real character name.
 *
 * @param model             bare model name, resolved the same way {@code NpcCommand.spawn} already does
 * @param skin              a real player's name/UUID for the model's {@code "skin"} material, or {@code null}
 * @param displayName       the in-game nameplate text — never {@code null} on this record even though
 *                          the AST field it comes from can be: {@code SmeNpcCompiler} already resolves
 *                          the "fall back to {@code name}" default before building this, so every
 *                          consumer of {@code NpcDefinition} gets a real string, never a null check
 * @param attributes        base-value overrides applied to every instance {@code npc_spawn}/{@code
 *                          npc_spawn_as} creates from this definition — lets a template like
 *                          {@code "dragon_boss"} carry its own tuned stats instead of every spawned
 *                          instance needing a separate {@code npc_set_attribute} call
 * @param onInteractFlowId      id of the {@code Flow} (already registered into {@code FlowRegistry}
 *                              by {@code SmeNpcCompiler}) to run when a player right-clicks the
 *                              spawned NPC, or {@code null} if the block declared no {@code onInteract}
 * @param onShiftInteractFlowId same as {@link #onInteractFlowId}, but for a Shift+right-click instead
 *                              — checked first by {@code NpcEntity#mobInteract}, so a plain click
 *                              still falls through to {@link #onInteractFlowId} unchanged
 */
public record NpcDefinition(String name, String model, String skin, NpcBehavior behavior, String displayName,
                             List<NpcAttributeSpec> attributes, Vec3 pos, ResourceLocation onInteractFlowId,
                             ResourceLocation onShiftInteractFlowId) {
}
