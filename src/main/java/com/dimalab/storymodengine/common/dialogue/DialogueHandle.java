package com.dimalab.storymodengine.common.dialogue;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * A narrow, read-only view of one running dialogue — mirrors {@code flow.FlowHandle} deliberately:
 * no {@link DialogueContext}/{@code Flow}/{@code Node} reachable from here, just enough for a
 * caller (a command, in the demo) to observe what's currently showing.
 */
public final class DialogueHandle {

    private final DialogueInstance instance;

    DialogueHandle(DialogueInstance instance) {
        this.instance = instance;
    }

    public ResourceLocation dialogueId() {
        return instance.dialogueId();
    }

    public DialogueState state() {
        return instance.state();
    }

    public boolean isWaitingForInput() {
        return state() == DialogueState.WAITING_FOR_INPUT;
    }

    public boolean isWaitingForChoice() {
        return state() == DialogueState.WAITING_FOR_CHOICE;
    }

    public boolean isCompleted() {
        return state() == DialogueState.COMPLETED;
    }

    public boolean isCancelled() {
        return state() == DialogueState.CANCELLED;
    }

    public DialogueLine currentLine() {
        return instance.currentLine();
    }

    public List<DialogueChoice> currentChoices() {
        return instance.currentChoices();
    }
}
