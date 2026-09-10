package com.dimalab.storymodengine.common.quest.debug;

import com.dimalab.storymodengine.common.StoryModEngine;
import com.dimalab.storymodengine.common.flow.Flow;
import com.dimalab.storymodengine.common.flow.node.Node;
import com.dimalab.storymodengine.common.logging.EngineLog;
import com.dimalab.storymodengine.api.quest.ObjectiveState;
import com.dimalab.storymodengine.common.quest.Quest;
import com.dimalab.storymodengine.common.quest.QuestCompiler;
import com.dimalab.storymodengine.common.quest.QuestDefinition;
import com.dimalab.storymodengine.common.quest.QuestProgress;
import com.dimalab.storymodengine.api.quest.QuestState;
import com.dimalab.storymodengine.common.quest.objective.Objective;
import com.dimalab.storymodengine.common.quest.persistence.QuestProgressStore;
import com.dimalab.storymodengine.api.quest.reward.Reward;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /sme quest test} — this project has no JUnit setup (verified: no {@code
 * src/test}, no test dependency in {@code build.gradle}), so this is the same in-game,
 * assert-and-report substitute every other subsystem's own {@code debug}/{@code selftest} command
 * uses (see {@code raycast.debug.RaycastSelfTestCommand}, {@code trigger.debug
 * .TriggerSelfTestCommand}). Two independent checks: {@link #testCompiler} (pure JVM —
 * does {@link QuestCompiler} produce a real, instantiable {@link Flow} for every step/reward shape,
 * including a branch) and {@link #testProgress} (exercises the real {@link QuestProgressStore}
 * against the invoking player, since a live {@code ServerPlayer}/capability is available here that
 * a pure-JVM test wouldn't have).
 */
@Mod.EventBusSubscriber(modid = StoryModEngine.MODID)
public final class QuestCompilerTestCommand {

    private static final ResourceLocation TEST_QUEST_ID = new ResourceLocation(StoryModEngine.MODID, "__compiler_test__");

    private QuestCompilerTestCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("sme")
                .then(Commands.literal("quest")
                        .then(Commands.literal("test").executes(QuestCompilerTestCommand::run))));
    }

    private static int run(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        List<String> failures = new ArrayList<>();

        testCompiler(failures);
        testProgress(player, failures);

        if (failures.isEmpty()) {
            EngineLog.channel("Quest").success("quest test: all checks passed");
            context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("[Quest] test: all checks passed"), false);
        } else {
            String joined = String.join("; ", failures);
            EngineLog.channel("Quest").error("quest test: {} failure(s): {}", failures.size(), joined);
            context.getSource().sendFailure(net.minecraft.network.chat.Component.literal(
                    "[Quest] test: " + failures.size() + " failure(s) — see log"));
        }
        return failures.isEmpty() ? 1 : 0;
    }

    /** Pure JVM: no player/capability/network touched — proves QuestCompiler produces a real, instantiable Flow for every step shape this system supports. */
    private static void testCompiler(List<String> failures) {
        QuestDefinition simple = Quest.define(TEST_QUEST_ID)
                .title("Compiler Test")
                .objective(Objective.kill("minecraft:zombie", 2))
                .objective(Objective.collect("minecraft:diamond", 1))
                .reward(Reward.experience(10))
                .build();
        checkCompiles(simple, "simple (kill+collect+reward)", failures);

        QuestDefinition branching = Quest.define(new ResourceLocation(StoryModEngine.MODID, "__compiler_test_branch__"))
                .title("Compiler Test — Branch")
                .branch(ctx -> true, Objective.kill("minecraft:zombie", 1), Objective.collect("minecraft:diamond", 1))
                .reward(Reward.item("minecraft:diamond", 1))
                .build();
        checkCompiles(branching, "branching", failures);

        QuestDefinition chained = Quest.define(new ResourceLocation(StoryModEngine.MODID, "__compiler_test_chain__"))
                .title("Compiler Test — Chain")
                .objective(Objective.quest(TEST_QUEST_ID))
                .reward(Reward.unlockQuest(TEST_QUEST_ID))
                .build();
        checkCompiles(chained, "quest-completion objective + unlockQuest reward", failures);
    }

    private static void checkCompiles(QuestDefinition quest, String label, List<String> failures) {
        try {
            Flow flow = QuestCompiler.compile(quest);
            if (flow == null) {
                failures.add(label + ": QuestCompiler.compile returned null");
                return;
            }
            Node root = flow.instantiate();
            if (root == null) {
                failures.add(label + ": Flow.instantiate() returned null");
            }
        } catch (RuntimeException e) {
            failures.add(label + ": threw " + e);
        }
    }

    /** Exercises the real QuestProgressStore against the invoking player — increment clamping, state transitions, and the record-replacement semantics QuestProgress's own Javadoc promises. */
    private static void testProgress(ServerPlayer player, List<String> failures) {
        QuestProgressStore.startQuest(player, TEST_QUEST_ID);
        QuestProgress started = QuestProgressStore.get(player, TEST_QUEST_ID);
        checkEquals(failures, "startQuest: state", QuestState.ACTIVE, started == null ? null : started.state());

        int afterFirst = QuestProgressStore.incrementObjective(player, TEST_QUEST_ID, "obj", 1, 3);
        checkEquals(failures, "increment 1/3", 1, afterFirst);
        int afterSecond = QuestProgressStore.incrementObjective(player, TEST_QUEST_ID, "obj", 1, 3);
        checkEquals(failures, "increment 2/3", 2, afterSecond);
        int overshoot = QuestProgressStore.incrementObjective(player, TEST_QUEST_ID, "obj", 10, 3);
        checkEquals(failures, "increment clamps to max", 3, overshoot);

        QuestProgressStore.completeObjective(player, TEST_QUEST_ID, "obj", 3);
        QuestProgress afterComplete = QuestProgressStore.get(player, TEST_QUEST_ID);
        checkEquals(failures, "completeObjective: state", ObjectiveState.COMPLETED,
                afterComplete == null ? null : afterComplete.objectiveStates().get("obj"));
        checkEquals(failures, "completeObjective: progress pinned to required", 3,
                afterComplete == null ? -1 : afterComplete.objectiveProgress().getOrDefault("obj", -1));

        QuestProgressStore.setQuestState(player, TEST_QUEST_ID, QuestState.COMPLETED);
        checkEquals(failures, "isCompleted after setQuestState", true, QuestProgressStore.isCompleted(player, TEST_QUEST_ID));
    }

    private static void checkEquals(List<String> failures, String label, Object expected, Object actual) {
        if (!java.util.Objects.equals(expected, actual)) {
            failures.add(label + ": expected " + expected + ", got " + actual);
        }
    }
}
