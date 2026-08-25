package com.dimalab.storymodengine.common.dialogue;

import java.util.List;

/**
 * Several {@link DialogueChoice}s offered together at one branch point — several consecutive
 * {@code .choice(...)} builder calls under one node collapse into a single group, so they render
 * and compile as one list the player picks from, not as separate sequential decisions.
 */
public record DialogueChoiceGroup(List<DialogueChoice> choices) implements DialogueEntry {
}
