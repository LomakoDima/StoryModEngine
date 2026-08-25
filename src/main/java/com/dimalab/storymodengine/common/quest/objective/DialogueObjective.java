package com.dimalab.storymodengine.common.quest.objective;

import com.dimalab.storymodengine.common.dialogue.DialogueDefinition;
import com.dimalab.storymodengine.common.dialogue.DialogueSystem;
import com.dimalab.storymodengine.common.dialogue.event.DialogueCompletedEvent;
import com.dimalab.storymodengine.common.dialogue.registry.DialogueRegistry;
import com.dimalab.storymodengine.common.flow.Flow;
import com.dimalab.storymodengine.common.logging.EngineLog;
import com.dimalab.storymodengine.api.quest.ObjectiveState;
import com.dimalab.storymodengine.common.quest.persistence.QuestProgressStore;
import net.minecraft.resources.ResourceLocation;

/**
 * {@code Objective.dialogue(dialogueId)} — starts an existing {@code DialogueDefinition} (never a
 * new dialogue runtime, per the task's own core constraint) and waits for {@link
 * DialogueCompletedEvent}, the exact structural analogue of {@code CinematicManager.playSequence}'s
 * "start X, {@code Flow.waitForEvent(XCompletedEvent, ...)}" pattern for cutscenes (verified: no
 * such pattern existed for Dialogue before this class — see the design doc §1).
 *
 * <p>{@link DialogueCompletedEvent} carries no outcome/choice information (verified from source —
 * only completed-vs-cancelled is tracked at the Dialogue layer) — a quest that needs to branch on
 * *which* choice was taken should attach a {@code DialogueCommand} to that specific choice instead
 * (see the design doc §2's documented workaround), not try to inspect this objective's completion.
 */
final class DialogueObjective implements Objective {

    private final String id;
    private final ResourceLocation dialogueId;

    DialogueObjective(ResourceLocation dialogueId) {
        this.id = "dialogue_" + ObjectiveSupport.sanitize(dialogueId);
        this.dialogueId = dialogueId;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String description() {
        return "Dialogue: " + dialogueId;
    }

    @Override
    public Flow compile(ResourceLocation questId) {
        return Flow.sequence(
                Flow.action(ctx -> QuestProgressStore.setObjectiveState(ctx.player(), questId, id, ObjectiveState.ACTIVE)),
                Flow.action(ctx -> {
                    DialogueDefinition definition = DialogueRegistry.get(dialogueId);
                    if (definition == null) {
                        EngineLog.channel("Quest").error("Objective.dialogue({}): no dialogue registered under this id", dialogueId);
                        return;
                    }
                    DialogueSystem.start(ctx.player(), definition);
                }),
                Flow.waitForEvent(DialogueCompletedEvent.class, (ctx, event) ->
                        event.player().equals(ctx.player()) && event.dialogueId().equals(dialogueId)),
                Flow.action(ctx -> QuestProgressStore.completeObjective(ctx.player(), questId, id, requiredCount()))
        );
    }
}
