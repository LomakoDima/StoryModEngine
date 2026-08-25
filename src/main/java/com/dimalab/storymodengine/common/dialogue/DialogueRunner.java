package com.dimalab.storymodengine.common.dialogue;

import com.dimalab.storymodengine.api.dialogue.DialogueCommand;
import com.dimalab.storymodengine.common.flow.Flow;
import com.dimalab.storymodengine.common.flow.FlowContext;
import com.dimalab.storymodengine.common.flow.Transition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Compiles a {@link DialogueDefinition} into an ordinary {@code Flow} — the whole adapter layer
 * this system exists to be. Every {@link DialogueEntry} maps onto an existing {@code flow}
 * primitive, never a new {@code Node} subtype:
 *
 * <ul>
 *   <li>{@link DialogueLine} → show it, then {@code Flow.choice(new Transition("continue", next))}
 *       — "wait for Continue" is just a one-option {@code Choice}, the same primitive a real branch
 *       uses.</li>
 *   <li>{@link DialogueChoiceGroup} → show it, then {@code Flow.choice(...)} with one {@code
 *       Transition} per offered {@link DialogueChoice}.</li>
 *   <li>{@link DialogueAction} → {@code Flow.action(...)} running the wrapped {@link
 *       DialogueCommand}.</li>
 *   <li>{@link DialogueConditionEntry} → {@code Flow.condition(...)} directly.</li>
 *   <li>{@link DialogueJump}/a choice's {@code targetNodeId} → {@code Flow.lazy(() ->
 *       nodeFlows.get(targetId))}, resolved only once the whole definition has finished compiling —
 *       see {@code Flow.lazy}'s own Javadoc for why a forward/cyclic node reference needs this.</li>
 *   <li>{@link DialogueEnd}, or simply running out of entries → a terminal {@code Flow.action(...)}
 *       marking the dialogue complete.</li>
 * </ul>
 *
 * <p>Compiled once per {@link DialogueDefinition} and cached by {@link DialogueSystem} — the same
 * shared {@code Flow} value backs every player's independent run, exactly the property {@code Flow}
 * itself is built around. Every generated {@code Flow.action} looks up "whichever {@link
 * DialogueInstance} is currently active for this context's player" via {@link
 * DialogueSystem#activeInstance} at the moment it actually runs, rather than capturing one
 * instance at compile time — the compiled {@code Flow} is shared across players and across repeated
 * runs, so nothing about it may close over one specific run's mutable state.
 */
final class DialogueRunner {

    private DialogueRunner() {
    }

    static Flow compile(DialogueDefinition definition) {
        Map<String, Flow> nodeFlows = new HashMap<>();
        for (DialogueNode node : definition.nodes().values()) {
            nodeFlows.put(node.id(), compileEntries(definition, node.id(), node.entries(), 0, nodeFlows));
        }
        return Flow.lazy(() -> nodeFlows.get(definition.startNodeId()));
    }

    private static Flow compileEntries(DialogueDefinition definition, String nodeId, List<DialogueEntry> entries, int index, Map<String, Flow> nodeFlows) {
        if (index >= entries.size()) {
            return terminal(definition);
        }

        DialogueEntry entry = entries.get(index);
        int nextIndex = index + 1;
        // Eager, not Flow.lazy: this recursion stays within one node's own finite entry list (no
        // cross-node reference, so no cycle risk), and computing it once here lets the resulting
        // Flow value be shared as-is across every Transition that falls through to it, instead of
        // recompiling an equivalent subtree on every Flow.lazy resolution (see that method's Javadoc
        // — it exists specifically for the genuinely cyclic/forward case, node-to-node jumps below).
        Flow continuation = compileEntries(definition, nodeId, entries, nextIndex, nodeFlows);

        if (entry instanceof DialogueLine line) {
            return compileLine(definition, nodeId, index, line, continuation);
        }
        if (entry instanceof DialogueChoiceGroup group) {
            return compileChoiceGroup(definition, nodeId, index, group, continuation, nodeFlows);
        }
        if (entry instanceof DialogueAction action) {
            return Flow.sequence(runCommand(definition, action.command()), continuation);
        }
        if (entry instanceof DialogueConditionEntry conditionEntry) {
            return Flow.sequence(Flow.condition(conditionEntry.condition()), continuation);
        }
        if (entry instanceof DialogueJump jump) {
            return Flow.lazy(() -> nodeFlows.get(jump.targetNodeId()));
        }
        if (entry instanceof DialogueEnd) {
            return terminal(definition);
        }
        throw new IllegalStateException("Unknown DialogueEntry: " + entry.getClass());
    }

    private static Flow compileLine(DialogueDefinition definition, String nodeId, int entryIndex, DialogueLine line, Flow continuation) {
        Flow showLine = Flow.action(ctx -> {
            DialogueInstance instance = DialogueSystem.activeInstance(ctx.player());
            if (instance != null) {
                instance.showLine(nodeId, entryIndex, line);
            }
        });
        Flow waitForContinue = Flow.choice(new Transition("continue", continuation));
        return Flow.sequence(showLine, waitForContinue);
    }

    private static Flow compileChoiceGroup(DialogueDefinition definition, String nodeId, int entryIndex, DialogueChoiceGroup group, Flow fallThrough, Map<String, Flow> nodeFlows) {
        Flow showChoices = Flow.action(ctx -> {
            DialogueInstance instance = DialogueSystem.activeInstance(ctx.player());
            if (instance == null) {
                return;
            }
            List<DialogueChoice> available = new ArrayList<>();
            for (DialogueChoice choice : group.choices()) {
                if (choice.isAvailable(ctx)) {
                    available.add(choice);
                }
            }
            instance.showChoices(nodeId, entryIndex, available);
        });

        List<Transition> transitions = new ArrayList<>();
        for (DialogueChoice choice : group.choices()) {
            Flow target = choice.targetNodeId() != null
                    ? Flow.lazy(() -> nodeFlows.get(choice.targetNodeId()))
                    : fallThrough;
            Flow afterChoice = choice.command() == null ? target : Flow.sequence(runCommand(definition, choice.command()), target);
            transitions.add(new Transition(choice.id(), afterChoice));
        }

        return Flow.sequence(showChoices, Flow.choice(transitions.toArray(new Transition[0])));
    }

    private static Flow runCommand(DialogueDefinition definition, DialogueCommand command) {
        return Flow.action(ctx -> command.execute(new DialogueContext(ctx, definition.id())));
    }

    private static Flow terminal(DialogueDefinition definition) {
        return Flow.action(ctx -> {
            DialogueInstance instance = DialogueSystem.activeInstance(ctx.player());
            if (instance != null) {
                instance.markCompleted();
            }
        });
    }
}
