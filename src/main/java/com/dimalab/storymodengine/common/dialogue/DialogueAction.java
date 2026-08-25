package com.dimalab.storymodengine.common.dialogue;

import com.dimalab.storymodengine.api.dialogue.DialogueCommand;
/** Runs one {@link DialogueCommand} — compiles directly to {@code Flow.action(...)}. */
public record DialogueAction(DialogueCommand command) implements DialogueEntry {
}
