package com.dimalab.storymodengine.common.dialogue;

import com.dimalab.storymodengine.common.dialogue.event.DialogueChoiceOpenedEvent;
import com.dimalab.storymodengine.common.dialogue.event.DialogueLineStartedEvent;
import com.dimalab.storymodengine.common.event.Events;
import com.dimalab.storymodengine.common.flow.FlowHandle;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Mutable per-player dialogue bookkeeping — the "Instance" half of the definition/instance split
 * {@link DialogueDefinition}'s own Javadoc describes, and the layer that knows things the
 * underlying {@link FlowHandle} genuinely cannot: whether a parked {@code Choice} is a line's
 * "Continue" or a real branch, and which {@link DialogueChoice}s are currently offered (already
 * filtered by condition — see {@link DialogueRunner}). Never persisted in this version (see the
 * task's own "persistence not required" instruction) — {@link DialogueSystem} simply loses this
 * bookkeeping across a relog, though the underlying {@code Flow} itself resumes correctly.
 */
public final class DialogueInstance {

    private final ServerPlayer player;
    private final ResourceLocation dialogueId;
    private final DialogueDefinition definition;
    private FlowHandle flowHandle;

    private DialogueState state = DialogueState.NOT_STARTED;
    private String currentNodeId;
    private int currentEntryIndex = -1;
    private DialogueLine currentLine;
    private List<DialogueChoice> currentChoices = List.of();

    /**
     * {@code flowHandle} starts {@code null} and is filled in by {@link #attachHandle} right after
     * {@code FlowManager.start(...)} returns — that same call synchronously runs the compiled
     * {@code Flow}'s first action (e.g. the opening line's {@code showLine}), which looks this
     * instance up via {@link DialogueSystem#activeInstance}, so it must already be registered
     * there *before* a {@code FlowHandle} exists to give it.
     */
    DialogueInstance(ServerPlayer player, ResourceLocation dialogueId, DialogueDefinition definition) {
        this.player = player;
        this.dialogueId = dialogueId;
        this.definition = definition;
    }

    void attachHandle(FlowHandle flowHandle) {
        this.flowHandle = flowHandle;
    }

    public ServerPlayer player() {
        return player;
    }

    public ResourceLocation dialogueId() {
        return dialogueId;
    }

    public DialogueDefinition definition() {
        return definition;
    }

    public FlowHandle flowHandle() {
        return flowHandle;
    }

    public DialogueState state() {
        return state;
    }

    public String currentNodeId() {
        return currentNodeId;
    }

    public int currentEntryIndex() {
        return currentEntryIndex;
    }

    public DialogueLine currentLine() {
        return currentLine;
    }

    public List<DialogueChoice> currentChoices() {
        return currentChoices;
    }

    /** The offered choice matching {@code transitionId} ({@code "continue"} while waiting on a line, or a real choice id), or {@code null} if it isn't currently offered. */
    public DialogueChoice findChoice(String transitionId) {
        for (DialogueChoice choice : currentChoices) {
            if (choice.id().equals(transitionId)) {
                return choice;
            }
        }
        return null;
    }

    /**
     * Called from inside a compiled {@code Flow.action} — which may run synchronously (e.g. while
     * still inside {@code DialogueSystem.start}/{@code selectChoice}'s own call stack, via {@code
     * Choice.select}'s cascade into its target's {@code onStart}) or asynchronously, one or more
     * server ticks later (whenever a {@link DialogueAction}/{@link DialogueConditionEntry} sits
     * between two waiting points, since {@code Sequence} only advances past an already-completed
     * child on its *next* {@code onTick}, not within the same {@code start} call). Notifying {@link
     * DialogueSystem} from here — rather than the caller of {@code selectChoice}/{@code start}
     * assuming the new state is already visible — is what makes both timings work correctly with no
     * special-casing.
     */
    void showLine(String nodeId, int entryIndex, DialogueLine line) {
        currentNodeId = nodeId;
        currentEntryIndex = entryIndex;
        currentLine = line;
        currentChoices = List.of(new DialogueChoice("continue", "Continue"));
        state = DialogueState.WAITING_FOR_INPUT;
        Events.post(new DialogueLineStartedEvent(player, dialogueId, nodeId, entryIndex));
        DialogueSystem.notifyStep(this);
    }

    void showChoices(String nodeId, int entryIndex, List<DialogueChoice> availableChoices) {
        currentNodeId = nodeId;
        currentEntryIndex = entryIndex;
        currentLine = null;
        currentChoices = availableChoices;
        state = DialogueState.WAITING_FOR_CHOICE;
        Events.post(new DialogueChoiceOpenedEvent(player, dialogueId, nodeId, entryIndex, availableChoices.stream().map(DialogueChoice::id).toList()));
        DialogueSystem.notifyStep(this);
    }

    /** The flow is actively running past a {@link DialogueAction}/{@link DialogueConditionEntry} — not yet parked at a new line/choice. */
    void markPlaying() {
        state = DialogueState.PLAYING;
    }

    void markCompleted() {
        state = DialogueState.COMPLETED;
        DialogueSystem.notifyFinished(this, false);
    }

    void markCancelled() {
        state = DialogueState.CANCELLED;
        DialogueSystem.notifyFinished(this, true);
    }
}
