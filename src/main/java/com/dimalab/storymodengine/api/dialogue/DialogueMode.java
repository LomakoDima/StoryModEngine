package com.dimalab.storymodengine.api.dialogue;

/**
 * How a {@link DialogueLine} is delivered — purely a presentation hint, resolved client-side into a
 * {@link com.dimalab.storymodengine.client.dialogue.DialogueStyle} variant (real font {@code Style}
 * flags — italic/bold/scale — plus color, never a change to the text content itself). The runtime/
 * {@code Flow} compilation doesn't branch on this at all.
 */
public enum DialogueMode {
    NORMAL,
    THOUGHT,
    WHISPER,
    SHOUT,
    MUTTER
}
