package com.dimalab.storymodengine.common.dialogue;

/**
 * A dialogue's own run state — distinct from {@code flow.FlowRunState}, the same way {@code
 * cinematic}'s playback states are distinct from the {@code Node} tree driving them underneath.
 * {@code WAITING_FOR_INPUT}/{@code WAITING_FOR_CHOICE} both correspond to the same underlying {@code
 * Flow} state (a parked {@code Choice}) — {@link DialogueInstance} is what tells them apart, since
 * {@code Choice} itself has no concept of "this is a line-continue vs. a real branch."
 */
public enum DialogueState {
    NOT_STARTED,
    PLAYING,
    WAITING_FOR_INPUT,
    WAITING_FOR_CHOICE,
    COMPLETED,
    CANCELLED
}
