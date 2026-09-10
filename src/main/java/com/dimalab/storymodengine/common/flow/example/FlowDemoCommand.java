package com.dimalab.storymodengine.common.flow.example;

import com.dimalab.storymodengine.common.StoryModEngine;
import com.dimalab.storymodengine.common.event.Events;
import com.dimalab.storymodengine.common.flow.FlowHandle;
import com.dimalab.storymodengine.common.flow.FlowManager;
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
 * {@code /sme flowdemo start|status|choice <id>|signal|pause|resume|cancel} — the
 * required demonstration of the expanded runtime, exercising {@code Choice}, {@code Wait}, {@code
 * SubFlow}, and {@code EventWaiter} together in one Flow ({@link FlowDemoScenario#DEMO}, found
 * automatically via {@code @AutoFlow} — nothing here registers it). {@code pause}/{@code resume}/
 * {@code cancel} exercise {@link FlowHandle} directly against whichever flow is currently active.
 */
@Mod.EventBusSubscriber(modid = StoryModEngine.MODID)
public final class FlowDemoCommand {

    private FlowDemoCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("sme")
                .then(Commands.literal("flowdemo")
                        .then(Commands.literal("start").executes(FlowDemoCommand::start))
                        .then(Commands.literal("status").executes(FlowDemoCommand::status))
                        .then(Commands.literal("choice")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .executes(FlowDemoCommand::choice)))
                        .then(Commands.literal("signal").executes(FlowDemoCommand::signal))
                        .then(Commands.literal("pause").executes(FlowDemoCommand::pause))
                        .then(Commands.literal("resume").executes(FlowDemoCommand::resume))
                        .then(Commands.literal("cancel").executes(FlowDemoCommand::cancel))));
    }

    private static int start(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        FlowHandle handle = FlowManager.start(FlowDemoScenario.ID, player);
        if (handle == null) {
            EngineLog.channel("Flow").error("Failed to start the demo flow — see console").toChat(player);
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
        boolean selected = FlowManager.selectChoice(player, FlowDemoScenario.ID, id);
        if (!selected) {
            EngineLog.channel("Flow").error("Choice '{}' was not accepted — see console", id).toChat(player);
            return 0;
        }
        return 1;
    }

    private static int signal(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Events.post(new FlowDemoSignalEvent(player));
        EngineLog.channel("Flow").success("Signal sent").toChat(player);
        return 1;
    }

    private static int pause(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return withHandle(context, FlowHandle::pause);
    }

    private static int resume(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return withHandle(context, FlowHandle::resume);
    }

    private static int cancel(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return withHandle(context, FlowHandle::cancel);
    }

    private static int withHandle(CommandContext<CommandSourceStack> context, java.util.function.Consumer<FlowHandle> action) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        var active = FlowManager.getActiveFlows(player);
        FlowHandle handle = active.stream().filter(h -> h.flowId().equals(FlowDemoScenario.ID)).findFirst().orElse(null);
        if (handle == null) {
            EngineLog.channel("Flow").error("No active demo flow").toChat(player);
            return 0;
        }
        action.accept(handle);
        EngineLog.channel("Flow").info("Flow {}: {}", handle.flowId(), handle.state()).toChat(player);
        return 1;
    }
}
