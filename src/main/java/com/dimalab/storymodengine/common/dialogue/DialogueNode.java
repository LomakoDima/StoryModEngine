package com.dimalab.storymodengine.common.dialogue;

import java.util.List;

/** One node in a {@link DialogueDefinition} — an id and its ordered {@link DialogueEntry} list. */
public record DialogueNode(String id, List<DialogueEntry> entries) {

    public DialogueNode {
        entries = List.copyOf(entries);
    }
}
