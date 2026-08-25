package com.dimalab.storymodengine.common.dialogue;

import com.dimalab.storymodengine.api.dialogue.DialogueCommand;
import com.dimalab.storymodengine.api.flow.Evaluator;

/**
 * One option offered at a branch point — the dialogue-level counterpart of {@code flow.Transition},
 * with the extra fields a dialogue choice needs that a bare {@code Transition} doesn't: display
 * {@code text}, an optional {@code condition} (re-evaluated server-side before the choice is
 * accepted — see {@link DialogueSystem#selectChoice}, never trusted from the client), and an
 * optional {@code command} run the instant the choice is taken. {@code targetNodeId} is nullable:
 * {@code null} means "fall through to whatever entry comes next in the same node" rather than
 * jumping elsewhere, so a choice doesn't have to always restart at a new node.
 */
public record DialogueChoice(
        String id,
        String text,
        Evaluator<Boolean> condition,
        String targetNodeId,
        DialogueCommand command
) {

    public DialogueChoice(String id, String text) {
        this(id, text, null, null, null);
    }

    /** Whether this choice is currently offered — {@code true} if it has no condition at all. */
    public boolean isAvailable(com.dimalab.storymodengine.common.flow.FlowContext flowContext) {
        return condition == null || Boolean.TRUE.equals(condition.evaluate(flowContext));
    }
}
