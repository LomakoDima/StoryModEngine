package com.dimalab.storymodengine.common.concurrent.debug;

import com.dimalab.storymodengine.api.concurrent.ExecutorKind;
import com.dimalab.storymodengine.common.StoryModEngine;
import com.dimalab.storymodengine.common.concurrent.AsyncDebug;
import com.dimalab.storymodengine.common.concurrent.AsyncStats;
import com.dimalab.storymodengine.common.concurrent.TaskInfo;
import com.dimalab.storymodengine.common.logging.EngineLog;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * {@code /storymodengine async list|info <id>|stats|debug <on|off>} — the in-game window onto
 * {@link AsyncDebug}, mirroring {@code trigger.example.TriggerDemoCommand}'s role for this
 * subsystem. Entirely optional: nothing here is on any hot path, and {@code debug off} (the default)
 * simply means {@link AsyncDebug#history()} stays empty — live-task listing and {@link
 * AsyncDebug#stats()} always work regardless.
 */
@Mod.EventBusSubscriber(modid = StoryModEngine.MODID)
public final class AsyncDebugCommand {

    private AsyncDebugCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("storymodengine")
                .then(Commands.literal("async")
                        .then(Commands.literal("list").executes(AsyncDebugCommand::list))
                        .then(Commands.literal("info")
                                .then(Commands.argument("id", LongArgumentType.longArg())
                                        .executes(AsyncDebugCommand::info)))
                        .then(Commands.literal("stats").executes(AsyncDebugCommand::stats))
                        .then(Commands.literal("debug")
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(AsyncDebugCommand::setDebug)))));
    }

    private static int list(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        List<TaskInfo> live = AsyncDebug.live();
        if (live.isEmpty()) {
            EngineLog.channel("Async").info("No live tasks.").toChat(player);
            return 1;
        }
        for (TaskInfo info : live) {
            EngineLog.channel("Async").info("#{} {} [{}] {} — {}ms", info.id(), info.name(), info.kind(), info.state(), info.durationMillis()).toChat(player);
        }
        return live.size();
    }

    private static int info(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        long id = LongArgumentType.getLong(context, "id");
        TaskInfo info = AsyncDebug.find(id);
        if (info == null) {
            EngineLog.channel("Async").error("No task #{} (live or in recent history — enable with /storymodengine async debug true)", id).toChat(player);
            return 0;
        }
        EngineLog.channel("Async").info("#{} {} [{}] {} — parent={} created={} duration={}ms error={}",
                info.id(), info.name(), info.kind(), info.state(), info.parentId(), info.createdAtNanos(),
                info.durationMillis(), info.errorSummary()).toChat(player);
        return 1;
    }

    private static int stats(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        AsyncStats stats = AsyncDebug.stats();
        EngineLog.channel("Async").info("live={} resumeQueue={} tickSchedule={} debugHistory={}({} entries)",
                stats.liveTasks(), stats.resumeQueueDepth(), stats.tickScheduleDepth(),
                stats.debugHistoryEnabled(), stats.debugHistorySize()).toChat(player);
        for (ExecutorKind kind : ExecutorKind.values()) {
            AsyncStats.KindStats k = stats.byKind().get(kind);
            EngineLog.channel("Async").info("{}: submitted={} completed={} failed={} cancelled={} rejected={} pool={}/{} queued={}",
                    kind, k.submitted(), k.completed(), k.failed(), k.cancelled(), k.rejected(),
                    k.activeCount(), k.poolSize(), k.queuedCount()).toChat(player);
        }
        return 1;
    }

    private static int setDebug(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        boolean enabled = BoolArgumentType.getBool(context, "enabled");
        AsyncDebug.setEnabled(enabled);
        EngineLog.channel("Async").success("debug history {}", enabled ? "enabled" : "disabled").toChat(player);
        return 1;
    }
}
