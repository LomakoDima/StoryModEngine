package com.dimalab.storymodengine.common.dialogue;

/**
 * One step inside a {@link DialogueNode}, in the order {@link DialogueRunner} compiles them.
 * Sealed so {@link DialogueRunner}'s compiler switch is exhaustive at compile time — six kinds,
 * each mapping directly onto an existing {@code flow} primitive (see {@link DialogueRunner}'s class
 * doc), deliberately not a general-purpose AST node.
 */
public sealed interface DialogueEntry
        permits DialogueLine, DialogueChoiceGroup, DialogueAction, DialogueConditionEntry, DialogueJump, DialogueEnd {
}
