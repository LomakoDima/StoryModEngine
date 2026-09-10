package com.dimalab.storymodengine.common.trigger.example;

import com.dimalab.storymodengine.common.StoryModEngine;
import com.dimalab.storymodengine.common.logging.EngineLog;
import com.dimalab.storymodengine.common.trigger.Trigger;
import com.dimalab.storymodengine.common.trigger.TriggerRegistry;
import com.dimalab.storymodengine.common.trigger.TriggerSystem;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;

/** {@code /sme trigger list|info|fire} — the task's own §10 debug API, common-side. */
@Mod.EventBusSubscriber(modid = StoryModEngine.MODID)
public final class TriggerDemoCommand {

    private TriggerDemoCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("sme")
                .then(Commands.literal("trigger")
                        .then(Commands.literal("list").executes(TriggerDemoCommand::list))
                        .then(Commands.literal("info")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .executes(TriggerDemoCommand::info)))
                        .then(Commands.literal("fire")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .executes(TriggerDemoCommand::fire)))));
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        Map<ResourceLocation, Trigger> all = TriggerRegistry.all();
        if (all.isEmpty()) {
            EngineLog.channel("Trigger").info("No triggers registered.").toChat(playerOrNull(context));
            return 0;
        }
        for (Trigger trigger : all.values()) {
            EngineLog.channel("Trigger").info(trigger.toString()).toChat(playerOrNull(context));
        }
        return all.size();
    }

    private static int info(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Trigger trigger = resolve(context);
        if (trigger == null) {
            EngineLog.channel("Trigger").error("No trigger with that id.").toChat(player);
            return 0;
        }
        EngineLog.channel("Trigger").info(
                "{} — kind={} policy={} persistent={} enabled={}",
                trigger.id(), trigger.kind(), trigger.policy(), trigger.persistent(), trigger.enabled()
        ).toChat(player);
        return 1;
    }

    private static int fire(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Trigger trigger = resolve(context);
        if (trigger == null) {
            EngineLog.channel("Trigger").error("No trigger with that id.").toChat(player);
            return 0;
        }
        TriggerSystem.fireForPlayer(trigger, player);
        EngineLog.channel("Trigger").success("Fired '{}' for you (bypassing its detector).", trigger.id()).toChat(player);
        return 1;
    }

    private static Trigger resolve(CommandContext<CommandSourceStack> context) {
        String path = StringArgumentType.getString(context, "id");
        ResourceLocation id = path.contains(":") ? new ResourceLocation(path) : new ResourceLocation(StoryModEngine.MODID, path);
        return TriggerRegistry.get(id);
    }

    private static ServerPlayer playerOrNull(CommandContext<CommandSourceStack> context) {
        try {
            return context.getSource().getPlayerOrException();
        } catch (CommandSyntaxException e) {
            return null;
        }
    }
}
