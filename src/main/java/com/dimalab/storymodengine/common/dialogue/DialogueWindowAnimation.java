package com.dimalab.storymodengine.common.dialogue;

/**
 * How {@code dialogue.client.DialogueWindow} transitions a line in/out — a presentation hint on
 * {@link DialogueLine} (nullable; falls back to whatever {@code
 * dialogue.client.DialogueStyle#animation()} the active style defaults to), the same pattern
 * {@link DialogueMode}/{@link TextRevealMode} already use. Deliberately not wired into the compiled
 * {@code Flow} at all — purely client rendering.
 */
public enum DialogueWindowAnimation {
    NONE,
    FADE,
    SLIDE
}
