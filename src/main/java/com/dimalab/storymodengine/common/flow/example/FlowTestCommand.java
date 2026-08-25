package com.dimalab.storymodengine.common.flow.example;

import com.dimalab.storymodengine.common.StoryModEngine;
import com.dimalab.storymodengine.common.flow.FlowHandle;
import com.dimalab.storymodengine.common.flow.FlowManager;
import com.dimalab.storymodengine.common.flow.registry.FlowRegistry;
import com.dimalab.storymodengine.common.logging.EngineLog;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * {@code /storymodengine flowtest start|choice <id>|status} — the required real in-game
 * demonstration. {@code start} runs {@link FlowExamples#STORY_TEST} until it parks at the
 * {@code Choice} step (a deliberate resting point — see {@code FlowState}'s Javadoc for why
 * persistence is demonstrated there rather than mid-{@code Action}); {@code status} prints the
 * flow's current state (including right after a save/reload, to prove restoration); {@code choice}
 * resolves the pending choice and finishes the flow.
 */
@Mod.EventBusSubscriber(modid = StoryModEngine.MODID)
public final class FlowTestCommand {

    static {
        FlowRegistry.register(FlowExamples.STORY_TEST_ID, FlowExamples.STORY_TEST);
    }

    private FlowTestCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("storymodengine")
                .then(Commands.literal("flowtest")
                        .then(Commands.literal("start").executes(FlowTestCommand::start))
                        .then(Commands.literal("status").executes(FlowTestCommand::status))
                        .then(Commands.literal("choice")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .executes(FlowTestCommand::choice)))));
    }

    private static int start(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        FlowHandle handle = FlowManager.start(FlowExamples.STORY_TEST_ID, player);
        if (handle == null) {
            EngineLog.channel("Flow").error("Failed to start flow — see console").toChat(player);
            return 0;
        }
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        var active = FlowManager.getActiveFlows(player);
        if (active.isEmpty()) {
            EngineLog.channel("Flow").info("No active flows").toChat(player);
            return 1;
        }
        for (FlowHandle handle : active) {
            EngineLog.channel("Flow").info("Flow {}: {}", handle.flowId(), handle.state()).toChat(player);
        }
        return 1;
    }

    private static int choice(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String id = StringArgumentType.getString(context, "id");
        boolean selected = FlowManager.selectChoice(player, FlowExamples.STORY_TEST_ID, id);
        if (!selected) {
            EngineLog.channel("Flow").error("Choice '{}' was not accepted — see console", id).toChat(player);
            return 0;
        }
        return 1;
    }
}
