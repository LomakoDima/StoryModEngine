package com.dimalab.storymodengine.common.quest.example;

import com.dimalab.storymodengine.common.StoryModEngine;
import com.dimalab.storymodengine.common.logging.EngineLog;
import com.dimalab.storymodengine.api.quest.ObjectiveState;
import com.dimalab.storymodengine.common.quest.QuestDefinition;
import com.dimalab.storymodengine.common.quest.QuestProgress;
import com.dimalab.storymodengine.common.quest.QuestRegistry;
import com.dimalab.storymodengine.common.quest.QuestSystem;
import com.dimalab.storymodengine.common.quest.objective.Objective;
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

/**
 * {@code /storymodengine quest list|start <id>|stop <id>|progress <id>} — the task's own §18
 * debug API, common-side (server-authoritative, same as every other quest entry point).
 */
@Mod.EventBusSubscriber(modid = StoryModEngine.MODID)
public final class QuestDemoCommand {

    private QuestDemoCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("storymodengine")
                .then(Commands.literal("quest")
                        .then(Commands.literal("list").executes(QuestDemoCommand::list))
                        .then(Commands.literal("start")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .executes(QuestDemoCommand::start)))
                        .then(Commands.literal("stop")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .executes(QuestDemoCommand::stop)))
                        .then(Commands.literal("progress")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .executes(QuestDemoCommand::progress)))));
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        Map<ResourceLocation, QuestDefinition> all = QuestRegistry.all();
        if (all.isEmpty()) {
            EngineLog.channel("Quest").info("No quests registered.").toChat(playerOrNull(context));
            return 0;
        }
        for (ResourceLocation id : all.keySet()) {
            EngineLog.channel("Quest").info(id.toString()).toChat(playerOrNull(context));
        }
        return all.size();
    }

    private static int start(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ResourceLocation id = resolve(StringArgumentType.getString(context, "id"));
        if (QuestSystem.start(player, id) == null) {
            EngineLog.channel("Quest").error("Failed to start '{}' — see the log", id).toChat(player);
            return 0;
        }
        return 1;
    }

    private static int stop(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ResourceLocation id = resolve(StringArgumentType.getString(context, "id"));
        QuestSystem.stop(player, id);
        return 1;
    }

    private static int progress(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ResourceLocation id = resolve(StringArgumentType.getString(context, "id"));
        QuestProgress progress = QuestSystem.progress(player, id);
        if (progress == null) {
            EngineLog.channel("Quest").info("'{}' has not been started.", id).toChat(player);
            return 0;
        }
        EngineLog.channel("Quest").info("'{}': {}", id, progress.state()).toChat(player);
        QuestDefinition quest = QuestRegistry.get(id);
        if (quest != null) {
            for (Objective objective : quest.objectives()) {
                ObjectiveState state = progress.objectiveStates().getOrDefault(objective.id(), ObjectiveState.INACTIVE);
                int current = progress.objectiveProgress().getOrDefault(objective.id(), 0);
                EngineLog.channel("Quest").info("  {} — {} ({}/{})",
                        objective.description(), state, current, objective.requiredCount()).toChat(player);
            }
        }
        return 1;
    }

    private static ResourceLocation resolve(String path) {
        return path.contains(":") ? new ResourceLocation(path) : new ResourceLocation(StoryModEngine.MODID, path);
    }

    private static ServerPlayer playerOrNull(CommandContext<CommandSourceStack> context) {
        try {
            return context.getSource().getPlayerOrException();
        } catch (CommandSyntaxException e) {
            return null;
        }
    }
}
