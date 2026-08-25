package com.dimalab.storymodengine.common.quest.objective;

import com.dimalab.storymodengine.api.event.Event;
import com.dimalab.storymodengine.api.quest.QuestStep;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.BiPredicate;

/**
 * The extension point (task §16): a mod author's own {@code public final class DefeatBossObjective
 * implements Objective} is a first-class objective with **zero** changes anywhere in {@code
 * QuestCompiler} — it's never inspected by type, only ever called polymorphically through {@link
 * #compile}. Named {@code Objective} rather than the task's own illustrative {@code ObjectiveType}
 * — chosen to mirror {@code dialogue.DialogueCommand}, the established in-repo convention for an
 * interface that's also its own static-factory holder (`DialogueCommand.of(...)`, `.playCutscene
 * (...)`); see the design doc §2 for why.
 *
 * <p>Built-in ids are content-derived (e.g. {@code "kill_minecraft_zombie"}), not an incrementing
 * counter — stable across JVM restarts and unaffected by unrelated code elsewhere registering more
 * objectives first, which a shared global counter would not be. Two objectives of the *same* kind
 * and target within one quest will collide; not a concern for any objective this engine ships.
 */
public interface Objective extends QuestStep {

    /** Stable, unique within one quest — auto-derived by every built-in factory below. */
    String id();

    /** Player-facing text for {@code QuestToast}/{@code QuestTrackerOverlay}. */
    String description();

    /** How many times this objective must be satisfied — {@code 1} unless overridden. */
    default int requiredCount() {
        return 1;
    }

    // {@link #compile(ResourceLocation)} is inherited from QuestStep — see ObjectiveSupport for the
    // shared "activate, wait until satisfied, mark complete" shape every built-in objective uses.

    // --- built-in factories ---

    static Objective kill(ResourceLocation entityTypeId, int count) {
        return new KillObjective(entityTypeId, count);
    }

    static Objective kill(String entityTypeId, int count) {
        return kill(parse(entityTypeId), count);
    }

    static Objective collect(ResourceLocation itemId, int count) {
        return new CollectObjective(itemId, count);
    }

    static Objective collect(String itemId, int count) {
        return collect(parse(itemId), count);
    }

    static Objective talkTo(ResourceLocation entityTypeId) {
        return new TalkToObjective(entityTypeId);
    }

    static Objective talkTo(String entityTypeId) {
        return talkTo(parse(entityTypeId));
    }

    static Objective interact(ResourceLocation blockId) {
        return new InteractObjective(blockId);
    }

    static Objective interact(String blockId) {
        return interact(parse(blockId));
    }

    /** {@code radius} in blocks. */
    static Objective location(BlockPos pos, double radius) {
        return new LocationObjective(pos, radius);
    }

    /** {@code radius} in blocks — distinct from {@link #location}: this tracks a live entity of the given type being nearby, not fixed coordinates. */
    static Objective findEntity(ResourceLocation entityTypeId, double radius) {
        return new FindEntityObjective(entityTypeId, radius);
    }

    static Objective findEntity(String entityTypeId, double radius) {
        return findEntity(parse(entityTypeId), radius);
    }

    /** Starts {@code dialogueId} via the existing {@code DialogueSystem} and waits for it to complete — see {@link DialogueObjective}'s Javadoc. */
    static Objective dialogue(ResourceLocation dialogueId) {
        return new DialogueObjective(dialogueId);
    }

    static Objective dialogue(String dialogueId) {
        return dialogue(parse(dialogueId));
    }

    /** Waits for a *different, independently-started* quest to complete — see {@link QuestCompletionObjective}'s Javadoc for how this differs from {@code QuestDefinition.Builder#prerequisite}. */
    static Objective quest(ResourceLocation otherQuestId) {
        return new QuestCompletionObjective(otherQuestId);
    }

    static Objective quest(String otherQuestId) {
        return quest(parse(otherQuestId));
    }

    /** The escape hatch onto any engine {@link Event} not covered by a named factory above — see {@link EventObjective}. */
    static <E extends Event> Objective event(String id, String description, Class<E> eventType, BiPredicate<ServerPlayer, E> matches) {
        return new EventObjective<>(id, description, eventType, matches, 1);
    }

    static <E extends Event> Objective event(String id, String description, Class<E> eventType, BiPredicate<ServerPlayer, E> matches, int count) {
        return new EventObjective<>(id, description, eventType, matches, count);
    }

    private static ResourceLocation parse(String id) {
        ResourceLocation parsed = ResourceLocation.tryParse(id);
        if (parsed == null) {
            throw new IllegalArgumentException("Not a valid namespaced id: '" + id + "' — expected 'namespace:path'");
        }
        return parsed;
    }
}
