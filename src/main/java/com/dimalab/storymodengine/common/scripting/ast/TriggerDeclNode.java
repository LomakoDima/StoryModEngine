package com.dimalab.storymodengine.common.scripting.ast;

import java.util.List;

/**
 * {@code trigger <id> { when ... once [persistent] run { ... } }}.
 *
 * @param persistent whether {@code once}'s "already fired" state survives a server restart, wiring
 *                   into the existing {@code Trigger.Builder.persistent()} Java API — meaningless
 *                   (and ignored by {@code TriggerCompiler}) when {@link #policy} is {@code REPEAT}.
 */
public record TriggerDeclNode(SourcePos pos, String id, List<MetadataTag> tags, TriggerConditionNode when, TriggerPolicyKind policy, boolean persistent, List<StmtNode> runBody) implements SmeNode {
}
