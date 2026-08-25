package com.dimalab.storymodengine.common.flow;

/**
 * One named option a {@code Choice} node can be resolved to — {@code id} is what external code
 * (a command, a future dialogue UI) passes to select it; {@code target} is the {@link Flow} that
 * starts once chosen. Deliberately minimal: not a general graph edge, not a visual-scripting
 * concept — just "this label leads to this Flow".
 */
public record Transition(String id, Flow target) {
}
