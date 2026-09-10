package com.dimalab.storymodengine.common.scripting.ast;

/** One entry inside a {@link DialogueNodeNode} — mirrors {@code dialogue.DialogueEntry}'s own six-kind sealed shape one-for-one, since {@code DialogueCompiler} lowers each of these directly onto the matching {@code DialogueDefinition.Builder} call. */
public sealed interface DialogueEntryNode extends SmeNode
        permits LineEntryNode, ChoiceGroupNode, ActionEntryNode, GateEntryNode, JumpEntryNode, EndEntryNode {
}
