package com.dimalab.storymodengine.common.raycast.example;

import com.dimalab.storymodengine.common.StoryModEngine;
import com.dimalab.storymodengine.common.logging.EngineLog;
import com.dimalab.storymodengine.common.raycast.Raycast;
import com.dimalab.storymodengine.common.raycast.RaycastQuery;
import com.dimalab.storymodengine.common.raycast.RaycastResult;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * {@code /storymodengine raycast test|debug [distance]} — the spec's own §17 test command. Runs an
 * {@code .any()} cast from the invoking player (server-side, exercising the dedicated-server-safe
 * path) and reports the result to chat; {@code debug} runs the identical cast with {@link
 * RaycastQuery#debug} enabled, so a live server can confirm the particle trail only ever appears
 * when actually asked for (§13/§18's "debug doesn't fire without .debug()" case, verified manually
 * here rather than as a JVM unit test — see {@code ARCHITECTURE.md}'s {@code raycast} section for
 * why: this project has no JUnit setup, so an in-game debug command is the established substitute,
 * same as {@code quest.debug.QuestCompilerTestCommand}).
 */
@Mod.EventBusSubscriber(modid = StoryModEngine.MODID)
public final class RaycastDemoCommand {

    private static final double DEFAULT_DISTANCE = 20.0;

    private RaycastDemoCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("storymodengine")
                .then(Commands.literal("raycast")
                        .then(Commands.literal("test")
                                .executes(ctx -> run(ctx, DEFAULT_DISTANCE, false))
                                .then(Commands.argument("distance", DoubleArgumentType.doubleArg(0.1))
                                        .executes(ctx -> run(ctx, DoubleArgumentType.getDouble(ctx, "distance"), false))))
                        .then(Commands.literal("debug")
                                .executes(ctx -> run(ctx, DEFAULT_DISTANCE, true))
                                .then(Commands.argument("distance", DoubleArgumentType.doubleArg(0.1))
                                        .executes(ctx -> run(ctx, DoubleArgumentType.getDouble(ctx, "distance"), true))))));
    }

    private static int run(CommandContext<CommandSourceStack> context, double distance, boolean debug) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();

        RaycastQuery query = Raycast.from(player).distance(distance).any();
        if (debug) {
            query.debug(ParticleTypes.END_ROD);
        }
        RaycastResult result = query.cast();

        report(player, result);
        return result.isHit() ? 1 : 0;
    }

    private static void report(ServerPlayer player, RaycastResult result) {
        switch (result.type()) {
            case BLOCK -> EngineLog.channel("Raycast").success(
                            "Hit block {} ({}) at {} — {} blocks away", result.block().orElseThrow(),
                            result.blockState().orElseThrow(), formatPos(result.position()), formatDistance(result))
                    .toChat(player);
            case ENTITY -> EngineLog.channel("Raycast").success(
                            "Hit entity {} at {} — {} blocks away", result.entity().orElseThrow(),
                            formatPos(result.position()), formatDistance(result))
                    .toChat(player);
            case MISS -> EngineLog.channel("Raycast").info(
                            "Missed — nothing within {} blocks", formatDistance(result))
                    .toChat(player);
        }
    }

    private static String formatPos(Vec3 pos) {
        return String.format("%.1f, %.1f, %.1f", pos.x, pos.y, pos.z);
    }

    private static String formatDistance(RaycastResult result) {
        return String.format("%.1f", result.distance());
    }
}
