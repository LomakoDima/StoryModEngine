package com.dimalab.storymodengine.api.dialogue;

import com.dimalab.storymodengine.common.dialogue.DialogueContext;
import com.dimalab.storymodengine.common.cinematic.CinematicManager;
import com.dimalab.storymodengine.common.cinematic.CutsceneDefinition;
import com.dimalab.storymodengine.common.cinematic.registry.CutsceneRegistry;
import com.dimalab.storymodengine.common.cinematic.titlecard.TitleCard;
import com.dimalab.storymodengine.common.flow.FlowManager;
import com.dimalab.storymodengine.common.logging.EngineLog;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.function.Consumer;

/**
 * One side effect a dialogue can trigger — the escape hatch for "give item"/"set variable"/anything
 * else a mod author needs, plus a handful of named factories for the integrations the task calls out
 * explicitly (cutscene, title card, {@code Flow}). Deliberately not a command *language*: {@link
 * #of} is a plain {@code Consumer<DialogueContext>}, and every named factory here is a one-line
 * delegate to the engine's own existing facade ({@code CinematicManager}/{@code FlowManager}) — this
 * class owns no cutscene/titlecard/flow logic of its own.
 */
@FunctionalInterface
public interface DialogueCommand {

    void execute(DialogueContext context);

    static DialogueCommand of(Consumer<DialogueContext> action) {
        return action::accept;
    }

    /** Delegates to the existing {@code CinematicManager.play(...)} — no actor bindings, matching a dialogue's own {@code DialogueContext.bind} being a separate concern. */
    static DialogueCommand playCutscene(ResourceLocation cutsceneId) {
        return context -> {
            CutsceneDefinition definition = CutsceneRegistry.get(cutsceneId);
            if (definition == null) {
                EngineLog.channel("Dialogue").warn("playCutscene: no cutscene registered under {}", cutsceneId);
                return;
            }
            CinematicManager.play(context.player(), definition, Map.of());
        };
    }

    /** Delegates to the existing {@code CinematicManager.playTitleCard(...)}. */
    static DialogueCommand showTitleCard(TitleCard titleCard) {
        return context -> CinematicManager.playTitleCard(context.player(), titleCard);
    }

    /** Delegates to the existing {@code FlowManager.start(...)} — a dialogue command starting an unrelated top-level Flow. */
    static DialogueCommand startFlow(ResourceLocation flowId) {
        return context -> FlowManager.start(flowId, context.player());
    }

    /** Ends the current dialogue immediately, as if it had reached a {@code DialogueEnd} entry. */
    static DialogueCommand closeDialogue() {
        return DialogueContext::close;
    }
}
