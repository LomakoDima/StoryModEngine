package com.dimalab.storymodengine.common.multiblock.example;

import com.dimalab.storymodengine.common.StoryModEngine;
import com.dimalab.storymodengine.common.logging.EngineLog;
import com.dimalab.storymodengine.common.multiblock.MultiblockMatch;
import com.dimalab.storymodengine.common.multiblock.PatternRotation;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;

/**
 * {@code /storymodengine multiblock build|check|clear} — the required live demonstration of the
 * multiblock system, exercising {@link MultiblockExamples#DEMO}. {@code build} places the demo
 * structure with a fixed physical layout at the player's feet; {@code check} runs {@link
 * MultiblockExamples#DEMO}'s {@code find} (trying all four {@link PatternRotation}s) and reports
 * the match (or its absence); {@code clear} resets the footprint to air so the demo can be
 * re-run without manual cleanup. All three purely call into the public {@code multiblock} API —
 * nothing here is part of the system itself, exactly like {@code FlowDemoCommand} for {@code flow}.
 */
@Mod.EventBusSubscriber(modid = StoryModEngine.MODID)
public final class MultiblockDemoCommand {

    private MultiblockDemoCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("storymodengine")
                .then(Commands.literal("multiblock")
                        .then(Commands.literal("build").executes(MultiblockDemoCommand::build))
                        .then(Commands.literal("check").executes(MultiblockDemoCommand::check))
                        .then(Commands.literal("clear").executes(MultiblockDemoCommand::clear))));
    }

    private static int build(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        BlockPos origin = player.blockPosition();
        placeLayer(player.serverLevel(), origin, false);
        EngineLog.channel("Multiblock").success("Demo structure built.").toChat(player);
        EngineLog.channel("Multiblock").info("Origin: {}", origin).toChat(player);
        EngineLog.channel("Multiblock").info("Run /storymodengine multiblock check to detect it.").toChat(player);
        return 1;
    }

    private static int check(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        BlockPos origin = player.blockPosition();
        Optional<MultiblockMatch> found = MultiblockExamples.DEMO.find(player.serverLevel(), origin);
        if (found.isPresent()) {
            MultiblockMatch match = found.get();
            EngineLog.channel("Multiblock").success("Multiblock '{}' matched!", match.multiblock().name()).toChat(player);
            EngineLog.channel("Multiblock").info("Origin: {}", match.origin()).toChat(player);
            EngineLog.channel("Multiblock").info("Rotation: {}", match.rotation()).toChat(player);
            return 1;
        }
        EngineLog.channel("Multiblock").warn("Structure not found.").toChat(player);
        return 0;
    }

    private static int clear(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        BlockPos origin = player.blockPosition();
        placeLayer(player.serverLevel(), origin, true);
        EngineLog.channel("Multiblock").info("Demo structure cleared.").toChat(player);
        return 1;
    }

    private static void placeLayer(ServerLevel level, BlockPos origin, boolean clear) {
        String[] rows = MultiblockExamples.DEMO_LAYER;
        for (int z = 0; z < rows.length; z++) {
            String row = rows[z];
            for (int x = 0; x < row.length(); x++) {
                BlockPos pos = PatternRotation.NORTH.toWorldPos(origin, x, 0, z);
                BlockState state = clear ? Blocks.AIR.defaultBlockState()
                        : MultiblockExamples.blockFor(row.charAt(x)).defaultBlockState();
                level.setBlockAndUpdate(pos, state);
            }
        }
    }
}
