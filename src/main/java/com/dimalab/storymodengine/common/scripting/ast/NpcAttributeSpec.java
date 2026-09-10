package com.dimalab.storymodengine.common.scripting.ast;

/**
 * One {@code attribute "<id>" <value>} line inside an {@code npc { }} declaration (see {@link
 * NpcDeclNode#attributes()}) — a base-value override applied to every instance spawned from that
 * declaration, resolved and applied the same way the runtime {@code npc_set_attribute} command already
 * does (see {@code NpcScriptCommands}), just bundled into the template instead of requiring a separate
 * call after each spawn.
 *
 * @param attributeId bare id or namespaced (e.g. {@code "generic.max_health"} or
 *                    {@code "minecraft:generic.max_health"}) — resolved the same
 *                    bare-id-defaults-to-minecraft way every other id in this engine is
 * @param value       the base value to set via {@code AttributeInstance.setBaseValue}
 */
public record NpcAttributeSpec(String attributeId, double value) {
}
