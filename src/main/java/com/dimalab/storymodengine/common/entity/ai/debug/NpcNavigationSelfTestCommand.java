package com.dimalab.storymodengine.common.entity.ai.debug;

import com.dimalab.storymodengine.common.StoryModEngine;
import com.dimalab.storymodengine.common.entity.ai.NpcPathFinder;
import com.dimalab.storymodengine.common.logging.EngineLog;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /sme npc navselftest} — same in-game, assert-and-report substitute this project
 * always uses in place of JUnit (see {@code RaycastSelfTestCommand}'s own doc: no {@code src/test},
 * no test dependency in {@code build.gradle}).
 *
 * <p>Covers only {@link NpcPathFinder#distance} — the jump-cost malus that breaks A*'s tie between a
 * forced-jump route and a flatter one of similar length (see that class's own doc). That is the one
 * piece of this engine's "own navigation" (jump malus / door open-close / {@code npc pathdebug}
 * render — see {@code sme-vs-he.html}) that is pure {@link Node} math with no {@code Level}/{@code
 * Mob} dependency, so it is the only piece a command like this can meaningfully check.
 * {@code SmoothGroundNavigation.canMoveDirectly} is <b>not</b> covered here — it needs a real
 * {@code Level} for {@code noCollision}/{@code getBlockState}, and this project has no lightweight
 * mock world to give it one (the same category of gap {@code RaycastSelfTestCommand} documents about
 * itself: "nearly everything ... needs a real Level"). Worth knowing, not solved here.
 */
@Mod.EventBusSubscriber(modid = StoryModEngine.MODID)
public final class NpcNavigationSelfTestCommand {

    private NpcNavigationSelfTestCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("sme")
                .then(Commands.literal("npc")
                        .then(Commands.literal("navselftest").executes(NpcNavigationSelfTestCommand::run))));
    }

    private static int run(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        List<String> failures = new ArrayList<>();

        testFlatStepUnaffected(failures);
        testDownwardStepUnaffected(failures);
        testSingleBlockJumpPenalized(failures);
        testMultiBlockJumpScalesLinearly(failures);

        if (failures.isEmpty()) {
            EngineLog.channel("Navigation").success("selftest: all checks passed").toChat(player);
        } else {
            String joined = String.join("; ", failures);
            EngineLog.channel("Navigation").error("selftest: {} failure(s): {}", failures.size(), joined).toChat(player);
        }
        return failures.isEmpty() ? 1 : 0;
    }

    /** A same-height edge should cost exactly what vanilla's own {@code Node#distanceTo} already says — no malus added. */
    private static void testFlatStepUnaffected(List<String> failures) {
        Node from = new Node(0, 64, 0);
        Node to = new Node(1, 64, 0);
        float expected = from.distanceTo(to);
        float actual = finder().distance(from, to);
        checkClose(failures, "flat step", expected, actual);
    }

    /** The malus only breaks the tie toward flatter routes — a descending edge must not be penalized either. */
    private static void testDownwardStepUnaffected(List<String> failures) {
        Node from = new Node(0, 64, 0);
        Node to = new Node(1, 63, 0);
        float expected = from.distanceTo(to);
        float actual = finder().distance(from, to);
        checkClose(failures, "downward step", expected, actual);
    }

    /** §NpcPathFinder — a one-block rise adds exactly one JUMP_COST_MALUS_PER_BLOCK on top of the base distance. */
    private static void testSingleBlockJumpPenalized(List<String> failures) {
        Node from = new Node(0, 64, 0);
        Node to = new Node(1, 65, 0);
        float expected = from.distanceTo(to) + 0.5f;
        float actual = finder().distance(from, to);
        checkClose(failures, "single-block jump", expected, actual);
    }

    /** The malus scales linearly with the height difference, not a flat per-jump surcharge. */
    private static void testMultiBlockJumpScalesLinearly(List<String> failures) {
        Node from = new Node(0, 64, 0);
        Node to = new Node(0, 67, 1);
        float expected = from.distanceTo(to) + 0.5f * 3;
        float actual = finder().distance(from, to);
        checkClose(failures, "three-block jump", expected, actual);
    }

    private static NpcPathFinder finder() {
        return new NpcPathFinder(new WalkNodeEvaluator(), 100);
    }

    private static void checkClose(List<String> failures, String label, float expected, float actual) {
        if (Math.abs(expected - actual) > 1e-4f) {
            failures.add(label + ": expected " + expected + ", got " + actual);
        }
    }
}
