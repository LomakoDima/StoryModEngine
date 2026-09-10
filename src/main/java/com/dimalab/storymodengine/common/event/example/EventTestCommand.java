package com.dimalab.storymodengine.common.event.example;

import com.dimalab.storymodengine.common.StoryModEngine;
import com.dimalab.storymodengine.common.event.Events;
import com.dimalab.storymodengine.common.logging.EngineLog;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /sme eventtest priority|parent|cancel|instance|exception} — the server-side
 * halves of the required in-game Event System demonstration; {@code /sme eventtest
 * network} (client-side, since it needs to send a packet) is {@link EventTestClientCommand}. Every
 * static listener these subcommands trigger lives in {@link EventTestListeners} and was discovered
 * automatically — nothing here registers one. The one exception is {@link InstanceListenerExample},
 * registered exactly once below, since instance listeners are never auto-discovered (see its
 * Javadoc).
 */
@Mod.EventBusSubscriber(modid = StoryModEngine.MODID)
public final class EventTestCommand {

    private static final InstanceListenerExample INSTANCE_LISTENER = registerInstanceListener();

    private EventTestCommand() {
    }

    private static InstanceListenerExample registerInstanceListener() {
        InstanceListenerExample listener = new InstanceListenerExample();
        Events.register(listener);
        return listener;
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("sme")
                .then(Commands.literal("eventtest")
                        .then(Commands.literal("priority").executes(EventTestCommand::priority))
                        .then(Commands.literal("parent").executes(EventTestCommand::parent))
                        .then(Commands.literal("cancel").executes(EventTestCommand::cancel))
                        .then(Commands.literal("instance").executes(EventTestCommand::instance))
                        .then(Commands.literal("exception").executes(EventTestCommand::exception))));
    }

    private static int priority(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        List<String> order = new ArrayList<>();
        Events.post(new PlayerTestEvent(player, order));
        EngineLog.channel("Events").success("Priority order: {}", order).toChat(player);
        return 1;
    }

    private static int parent(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        List<String> order = new ArrayList<>();
        Events.post(new ChildTestEvent(player, order));
        EngineLog.channel("Events").success(
                "Parent + child dispatch order: {} (both PlayerTestEvent-typed and ChildTestEvent-typed listeners ran)", order)
                .toChat(player);
        return 1;
    }

    private static int cancel(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        List<String> order = new ArrayList<>();
        Events.post(new CancellableTestEvent(player, order));
        EngineLog.channel("Events").success(
                "Cancellation ran: {} (NORMAL/LOW must be absent — HIGH cancelled the event)", order).toChat(player);
        return 1;
    }

    private static int instance(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Events.post(new InstanceTestEvent(player));
        return 1;
    }

    private static int exception(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        List<String> order = new ArrayList<>();
        Events.post(new ExceptionTestEvent(player, order));
        EngineLog.channel("Events").success(
                "Exception isolation ran: {} (B threw — see the warn log — A and C still ran, game is alive)", order)
                .toChat(player);
        return 1;
    }
}
