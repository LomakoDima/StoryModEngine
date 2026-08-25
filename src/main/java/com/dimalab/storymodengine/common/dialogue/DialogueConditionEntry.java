package com.dimalab.storymodengine.common.dialogue;

import com.dimalab.storymodengine.api.flow.Evaluator;

/**
 * A simple in-node gate — compiles directly to {@code Flow.condition(...)}. If it fails, the whole
 * compiled node {@code Sequence} fails, which {@link DialogueRunner} treats as the dialogue ending
 * (posts {@code DialogueCancelledEvent}, closes the window) rather than a crash — the same
 * fail-propagation {@code Sequence}/{@code Condition} already have, nothing dialogue-specific added.
 */
public record DialogueConditionEntry(Evaluator<Boolean> condition) implements DialogueEntry {
}
