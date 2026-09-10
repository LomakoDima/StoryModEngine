package com.dimalab.storymodengine.common.scripting.ast;

import java.util.List;

/**
 * {@code npc "<name>" { model "..." skin "..." behavior "..." displayName "..." pos X Y Z onInteract { ... } }}.
 * Pure data — like {@link QuestDeclNode}/{@code DialogueDeclNode}, declaring this does not spawn
 * anything; a script (or a game-master command) spawns it later by name.
 *
 * @param name          the script-facing identifier every {@code npc_*} command resolves by
 * @param model         bare model name under {@code storymodengine/models/}, same convention {@code
 *                      NpcCommand.spawn} already uses
 * @param skin          a real player's name/UUID to render the model's {@code "skin"} material as,
 *                      or {@code null} for none
 * @param behavior      {@code passive}/{@code hostile}/{@code stationary}, or {@code null} for the
 *                      default ({@code passive})
 * @param displayName   the in-game nameplate text, or {@code null} to fall back to {@link #name} —
 *                      most scripts want the same human-readable string for both, but this lets a
 *                      script use a short internal lookup key with a different display name
 * @param attributes    zero or more repeated {@code attribute "<id>" <value>} lines — this is what
 *                      lets a template like {@code "dragon_boss"} carry its own tuned stats, applied
 *                      to every instance {@code npc_spawn_as} creates from it, not just the one
 *                      {@code npc_spawn} would give a single-instance declaration
 * @param onInteract      statement list run (as a {@code Flow}) when a player right-clicks the
 *                        spawned NPC — empty if the block declared none
 * @param onShiftInteract statement list run (as a separate {@code Flow}) when a player
 *                        Shift+right-clicks the spawned NPC instead of a plain right-click — empty
 *                        if the block declared none
 */
public record NpcDeclNode(SourcePos pos, String name, List<MetadataTag> tags, String model, String skin,
                           String behavior, String displayName, List<NpcAttributeSpec> attributes,
                           double x, double y, double z, List<StmtNode> onInteract,
                           List<StmtNode> onShiftInteract) implements SmeNode {
}
