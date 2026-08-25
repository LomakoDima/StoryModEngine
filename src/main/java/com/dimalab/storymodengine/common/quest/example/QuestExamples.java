package com.dimalab.storymodengine.common.quest.example;

import com.dimalab.storymodengine.common.StoryModEngine;
import com.dimalab.storymodengine.common.quest.Quest;
import com.dimalab.storymodengine.common.quest.QuestDefinition;
import com.dimalab.storymodengine.api.quest.annotation.AutoQuest;
import com.dimalab.storymodengine.common.quest.objective.Objective;
import com.dimalab.storymodengine.api.quest.reward.Reward;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;

/**
 * The required demonstration quests, built with the plain Java DSL — {@code @AutoQuest} registers
 * each automatically, no manual {@code QuestRegistry.register} call anywhere (proof by construction,
 * the same way {@code DialogueExamples}/{@code CutsceneExamples} prove their own {@code @Auto*}
 * annotations). This engine ships no custom entities/dialogues of its own for a quest to reference,
 * so real vanilla entity/item ids and the existing {@code dialogue.example.DialogueExamples
 * .VILLAGE_GUARD} definition stand in — same honest stance the {@code voxel}/{@code cinematic} demo
 * content already takes about shipping no bespoke art assets.
 */
public final class QuestExamples {

    /**
     * Covers: multiple objectives (4, of 3 different kinds), a real {@code Objective.dialogue(...)}
     * integration (starts the existing {@code village_guard} dialogue and waits for it), and
     * multiple rewards — including {@link Reward#unlockQuest} chaining into {@link #VILLAGE_TRIAL}.
     */
    @AutoQuest
    public static final QuestDefinition FIND_THE_RHINO = Quest.define(id("find_the_rhino"))
            .title("Find the Rhino")
            .description("A hunter has lost his rhino — help him find and deal with it.")
            .objective(Objective.talkTo("minecraft:villager"))
            .objective(Objective.dialogue(id("village_guard")))
            .objective(Objective.kill("minecraft:zombie", 3))
            .objective(Objective.collect("minecraft:diamond", 3))
            .reward(Reward.item("minecraft:diamond", 5))
            .reward(Reward.experience(100))
            .reward(Reward.unlockQuest(id("village_trial")))
            .build();

    /**
     * Covers: a {@link QuestDefinition.Builder#prerequisite} gate (won't start until {@link
     * #FIND_THE_RHINO} is completed — demonstrated *twice* over, alongside that quest's own {@code
     * Reward.unlockQuest} auto-start) and {@link QuestDefinition.Builder#branch} — donate if the
     * player already has enough diamonds, otherwise go collect them, compiling straight to {@code
     * Flow.branch} (see the design doc §4/§8).
     */
    @AutoQuest
    public static final QuestDefinition VILLAGE_TRIAL = Quest.define(id("village_trial"))
            .title("The Village Trial")
            .description("Prove yourself to the village — donate diamonds, or go earn them.")
            .prerequisite(id("find_the_rhino"))
            .branch(ctx -> ctx.player().getInventory().countItem(Items.DIAMOND) >= 5,
                    Objective.interact("minecraft:chest"),
                    Objective.collect("minecraft:diamond", 5))
            .reward(Reward.experience(250))
            .build();

    private QuestExamples() {
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(StoryModEngine.MODID, path);
    }
}
