package com.dimalab.storymodengine.client.dialogue;

/**
 * Purely client-side character-reveal progress — the runtime (server, compiled {@code Flow}) only
 * ever knows "line started"/"line completed" (see {@code DialogueLine}'s own Javadoc), never how
 * many characters are currently visible. One instance, reused for every line {@link DialogueWindow}
 * shows; {@link #start} resets it.
 */
final class TypewriterState {

    private String text = "";
    private int charactersPerTick = 1;
    private int revealed;

    void start(String text, int charactersPerTick) {
        this.text = text == null ? "" : text;
        this.charactersPerTick = Math.max(1, charactersPerTick);
        this.revealed = 0;
    }

    /** Advances reveal progress by one client tick. */
    void tick() {
        if (revealed < text.length()) {
            revealed = Math.min(text.length(), revealed + charactersPerTick);
        }
    }

    /** Instantly reveals the rest — the first "Continue" press while still animating. */
    void revealAll() {
        revealed = text.length();
    }

    boolean isComplete() {
        return revealed >= text.length();
    }

    String visibleText() {
        return text.substring(0, revealed);
    }
}
