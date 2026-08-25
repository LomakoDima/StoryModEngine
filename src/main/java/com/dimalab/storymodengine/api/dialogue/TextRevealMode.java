package com.dimalab.storymodengine.api.dialogue;

/**
 * How a {@link DialogueLine}'s text appears on screen — a purely client-side concern. The runtime
 * (server and {@link DialogueRunner}'s compiled {@code Flow}) only ever knows "line started"/"line
 * completed"; it has no notion of how many characters are currently revealed, INSTANT or otherwise.
 */
public enum TextRevealMode {
    INSTANT,
    TYPEWRITER
}
