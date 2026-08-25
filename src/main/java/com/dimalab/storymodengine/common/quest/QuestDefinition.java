package com.dimalab.storymodengine.common.quest;

import com.dimalab.storymodengine.api.quest.QuestStep;
import com.dimalab.storymodengine.api.flow.Evaluator;
import com.dimalab.storymodengine.common.quest.objective.Objective;
import com.dimalab.storymodengine.api.quest.reward.Reward;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * An immutable quest — id, title/description, optional prerequisites, an ordered list of steps
 * (objectives and branches), and rewards. Never holds runtime/progress state (see {@link
 * QuestProgress}) — the same definition/instance split {@code Flow}, {@code CutsceneDefinition}, and
 * {@code DialogueDefinition} all already establish, which is what lets one {@code QuestDefinition}
 * be started by any number of players independently.
 *
 * <pre>{@code
 * QuestDefinition quest = Quest.define(id("find_rhino"))
 *     .title("Find the Rhino")
 *     .objective(Objective.talkTo("modid:hunter"))
 *     .objective(Objective.dialogue("modid:hunter_intro"))
 *     .objective(Objective.kill("modid:rhino", 1))
 *     .reward(Reward.item("minecraft:diamond", 5))
 *     .build();
 * }</pre>
 */
public final class QuestDefinition {

    private final ResourceLocation id;
    private final String title;
    private final String description;
    private final List<ResourceLocation> prerequisites;
    private final List<QuestStep> steps;
    private final List<Reward> rewards;

    private QuestDefinition(ResourceLocation id, String title, String description,
                             List<ResourceLocation> prerequisites, List<QuestStep> steps, List<Reward> rewards) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.prerequisites = List.copyOf(prerequisites);
        this.steps = List.copyOf(steps);
        this.rewards = List.copyOf(rewards);
    }

    public ResourceLocation id() {
        return id;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    public List<ResourceLocation> prerequisites() {
        return prerequisites;
    }

    List<QuestStep> steps() {
        return steps;
    }

    public List<Reward> rewards() {
        return rewards;
    }

    /** Every {@link Objective} this quest can offer, flattened out of {@link #steps()} — a {@link BranchStep}'s two options both appear here, since either might end up being the one actually offered. UI/tracker display, never consulted by {@code QuestCompiler} (which walks {@link #steps()} directly). */
    public List<Objective> objectives() {
        List<Objective> result = new ArrayList<>();
        for (QuestStep step : steps) {
            if (step instanceof Objective objective) {
                result.add(objective);
            } else if (step instanceof BranchStep branch) {
                result.add(branch.ifTrue());
                result.add(branch.ifFalse());
            }
        }
        return List.copyOf(result);
    }

    public static Builder builder(ResourceLocation id) {
        return new Builder(id);
    }

    public static final class Builder {
        private final ResourceLocation id;
        private String title = "";
        private String description = "";
        private final List<ResourceLocation> prerequisites = new ArrayList<>();
        private final List<QuestStep> steps = new ArrayList<>();
        private final List<Reward> rewards = new ArrayList<>();

        Builder(ResourceLocation id) {
            this.id = id;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /** Consulted only by {@code QuestSystem.start} (never compiled into the Flow) — see the design doc §7. */
        public Builder prerequisite(ResourceLocation questId) {
            prerequisites.add(questId);
            return this;
        }

        public Builder prerequisite(String questId) {
            return prerequisite(parse(questId));
        }

        public Builder objective(Objective objective) {
            steps.add(objective);
            return this;
        }

        /** Compiles to {@code Flow.branch(condition, ...)} — see {@link BranchStep}'s Javadoc. */
        public Builder branch(Evaluator<Boolean> condition, Objective ifTrue, Objective ifFalse) {
            steps.add(new BranchStep(condition, ifTrue, ifFalse));
            return this;
        }

        public Builder reward(Reward reward) {
            rewards.add(reward);
            return this;
        }

        public QuestDefinition build() {
            if (steps.isEmpty()) {
                throw new IllegalStateException("Quest '" + id + "' has no objectives — call .objective(...) at least once");
            }
            return new QuestDefinition(id, title, description, prerequisites, steps, rewards);
        }

        private static ResourceLocation parse(String id) {
            ResourceLocation parsed = ResourceLocation.tryParse(id);
            if (parsed == null) {
                throw new IllegalArgumentException("Not a valid namespaced id: '" + id + "' — expected 'namespace:path'");
            }
            return parsed;
        }
    }
}
