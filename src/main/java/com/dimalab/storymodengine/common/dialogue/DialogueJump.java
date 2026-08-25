package com.dimalab.storymodengine.common.dialogue;

/** Unconditionally continues at another node — compiles to {@code Flow.lazy(() -> nodeFlows.get(targetNodeId))}. */
public record DialogueJump(String targetNodeId) implements DialogueEntry {
}
