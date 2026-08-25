package com.dimalab.storymodengine.common.dialogue;

/** Explicit terminal — compiles to a {@code Flow.action(...)} that marks the dialogue {@code COMPLETED}. */
public record DialogueEnd() implements DialogueEntry {

    public static final DialogueEnd INSTANCE = new DialogueEnd();
}
