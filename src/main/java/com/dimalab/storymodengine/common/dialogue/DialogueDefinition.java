package com.dimalab.storymodengine.common.dialogue;

import com.dimalab.storymodengine.api.dialogue.DialogueCommand;
import com.dimalab.storymodengine.api.flow.Evaluator;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An immutable dialogue — a {@code startNodeId} plus a {@code Map<String, DialogueNode>}, exactly
 * mirroring {@code flow.FlowDefinition}'s own "id + immutable graph" shape. Never holds a runtime
 * entity/actor reference directly (see {@link DialogueContext#bind}); never holds mutable playback
 * state (see {@link DialogueInstance}) — the same definition/instance split {@code Flow} itself
 * established, which is exactly what lets two players run the same {@code DialogueDefinition}
 * independently (see {@link DialogueRunner}).
 *
 * <pre>{@code
 * DialogueDefinition dialogue = DialogueDefinition.builder(id("village_guard"))
 *     .node("start")
 *         .line("Guard", "Halt! Who are you?")
 *         .choice("I'm a traveller.").gotoNode("traveller")
 *         .choice("None of your business.").gotoNode("rude")
 *     .node("traveller")
 *         .line("Guard", "Then welcome to the village.")
 *         .end()
 *     .node("rude")
 *         .line("Guard", "Then move along.")
 *         .end()
 *     .build();
 * }</pre>
 */
public final class DialogueDefinition {

    private final ResourceLocation id;
    private final String title;
    private final Map<String, DialogueNode> nodes;
    private final String startNodeId;

    private DialogueDefinition(ResourceLocation id, String title, Map<String, DialogueNode> nodes, String startNodeId) {
        this.id = id;
        this.title = title;
        this.nodes = Map.copyOf(nodes);
        this.startNodeId = startNodeId;
    }

    public ResourceLocation id() {
        return id;
    }

    public String title() {
        return title;
    }

    public Map<String, DialogueNode> nodes() {
        return nodes;
    }

    public DialogueNode node(String nodeId) {
        return nodes.get(nodeId);
    }

    public String startNodeId() {
        return startNodeId;
    }

    public static Builder builder(ResourceLocation id) {
        return new Builder(id);
    }

    /**
     * One stateful builder rather than several cooperating node/choice builder types — smaller
     * surface, and the chained shape the task's own DSL example needs ({@code .choice(text)}
     * immediately followed by {@code .gotoNode(id)}/{@code .condition(...)}/{@code .command(...)})
     * only needs one piece of mutable staging state ("the choice currently being described") plus
     * one accumulator ("choices offered together at this point"), not a tree of builder objects.
     * Several consecutive {@code .choice(...)} calls collapse into one {@link DialogueChoiceGroup};
     * the group is flushed into the current node's entries the moment anything else is called
     * ({@code .line}/{@code .action}/{@code .node}/{@code .end}/{@code .build}).
     */
    public static final class Builder {
        private final ResourceLocation id;
        private String title;
        private final Map<String, List<DialogueEntry>> nodeEntries = new LinkedHashMap<>();
        private String startNodeId;
        private String currentNodeId;

        private List<DialogueChoice> pendingGroup;
        private boolean hasPendingChoice;
        private String pendingId;
        private String pendingText;
        private Evaluator<Boolean> pendingCondition;
        private String pendingTarget;
        private DialogueCommand pendingCommand;

        private Builder(ResourceLocation id) {
            this.id = id;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder node(String nodeId) {
            closeChoiceGroup();
            currentNodeId = nodeId;
            if (startNodeId == null) {
                startNodeId = nodeId;
            }
            nodeEntries.computeIfAbsent(nodeId, key -> new ArrayList<>());
            return this;
        }

        public Builder line(String speaker, String text) {
            return line(new DialogueLine(speaker, text));
        }

        public Builder line(DialogueLine line) {
            closeChoiceGroup();
            requireNode().add(line);
            return this;
        }

        /** Starts describing one choice — follow with {@link #gotoNode}/{@link #condition}/{@link #command} as needed. Its id is auto-generated; use {@link #choice(String, String)} to set one explicitly (e.g. from JSON, where the id is authored). */
        public Builder choice(String text) {
            return choice(null, text);
        }

        /** Same as {@link #choice(String)}, with an explicit id instead of an auto-generated one. */
        public Builder choice(String id, String text) {
            flushPendingChoice();
            hasPendingChoice = true;
            pendingId = id;
            pendingText = text;
            pendingCondition = null;
            pendingTarget = null;
            pendingCommand = null;
            return this;
        }

        /** The node this choice jumps to; omit to fall through to whatever entry comes next in this node instead. */
        public Builder gotoNode(String targetNodeId) {
            requirePendingChoice("gotoNode");
            pendingTarget = targetNodeId;
            return this;
        }

        /** Re-evaluated server-side every time the choice is offered and every time it's selected — never trusted from the client. */
        public Builder condition(Evaluator<Boolean> condition) {
            requirePendingChoice("condition");
            pendingCondition = condition;
            return this;
        }

        /** Runs {@code command} the instant this specific choice is taken. */
        public Builder command(DialogueCommand command) {
            requirePendingChoice("command");
            pendingCommand = command;
            return this;
        }

        /** A side effect with no player-facing choice attached — always runs when reached. */
        public Builder action(DialogueCommand command) {
            closeChoiceGroup();
            requireNode().add(new DialogueAction(command));
            return this;
        }

        /** A gate: if {@code condition} fails here, the dialogue ends rather than continuing past this point. */
        public Builder gate(Evaluator<Boolean> condition) {
            closeChoiceGroup();
            requireNode().add(new DialogueConditionEntry(condition));
            return this;
        }

        /** Unconditionally continues at {@code targetNodeId}. */
        public Builder jump(String targetNodeId) {
            closeChoiceGroup();
            requireNode().add(new DialogueJump(targetNodeId));
            return this;
        }

        /** Explicitly ends the dialogue if this point is reached. */
        public Builder end() {
            closeChoiceGroup();
            requireNode().add(DialogueEnd.INSTANCE);
            return this;
        }

        public DialogueDefinition build() {
            closeChoiceGroup();
            if (startNodeId == null) {
                throw new IllegalStateException("DialogueDefinition '" + id + "' has no nodes — call .node(id) at least once");
            }
            Map<String, DialogueNode> nodes = new LinkedHashMap<>();
            nodeEntries.forEach((nodeId, entries) -> nodes.put(nodeId, new DialogueNode(nodeId, entries)));
            return new DialogueDefinition(id, title, nodes, startNodeId);
        }

        private List<DialogueEntry> requireNode() {
            if (currentNodeId == null) {
                throw new IllegalStateException("Call .node(id) before adding entries to a DialogueDefinition builder");
            }
            return nodeEntries.get(currentNodeId);
        }

        private void requirePendingChoice(String method) {
            if (!hasPendingChoice) {
                throw new IllegalStateException("." + method + "() must immediately follow .choice(text)");
            }
        }

        private void flushPendingChoice() {
            if (!hasPendingChoice) {
                return;
            }
            if (pendingGroup == null) {
                pendingGroup = new ArrayList<>();
            }
            String choiceId = pendingId != null ? pendingId : "choice_" + (pendingGroup.size() + 1);
            pendingGroup.add(new DialogueChoice(choiceId, pendingText, pendingCondition, pendingTarget, pendingCommand));
            hasPendingChoice = false;
        }

        private void closeChoiceGroup() {
            flushPendingChoice();
            if (pendingGroup != null && !pendingGroup.isEmpty()) {
                requireNode().add(new DialogueChoiceGroup(List.copyOf(pendingGroup)));
            }
            pendingGroup = null;
        }
    }
}
