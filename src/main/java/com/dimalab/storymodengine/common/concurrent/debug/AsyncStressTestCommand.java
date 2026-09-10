package com.dimalab.storymodengine.common.concurrent.debug;

import com.dimalab.storymodengine.common.StoryModEngine;
import com.dimalab.storymodengine.common.concurrent.Async;
import com.dimalab.storymodengine.common.concurrent.AsyncDebug;
import com.dimalab.storymodengine.common.concurrent.AsyncTask;
import com.dimalab.storymodengine.common.logging.EngineLog;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@code /sme async stresstest [count]} — 1,000 by default, hard-capped at {@link
 * #MAX_COUNT} by Brigadier's own range validation. Deliberately a separate command in a separate
 * class from {@code AsyncSelfTestCommand}: nothing here is ever invoked automatically (not by
 * {@code selftest}, not by any bootstrap), matching the requirement that load testing is opt-in,
 * manual, in-game verification only. Also exercises cancelling roughly one task in ten while the
 * batch is still in flight — the "concurrent cancellation under load" check the design calls for; a
 * cancel racing (or losing to) a task that already finished is a documented no-op, not an error.
 */
@Mod.EventBusSubscriber(modid = StoryModEngine.MODID)
public final class AsyncStressTestCommand {

    private static final int DEFAULT_COUNT = 1_000;
    private static final int MAX_COUNT = 10_000;

    private AsyncStressTestCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("sme")
                .then(Commands.literal("async")
                        .then(Commands.literal("stresstest")
                                .executes(context -> run(context, DEFAULT_COUNT))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, MAX_COUNT))
                                        .executes(context -> run(context, IntegerArgumentType.getInteger(context, "count")))))));
    }

    private static int run(CommandContext<CommandSourceStack> context, int count) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        EngineLog.channel("Async").info("stresstest: submitting {} short-lived tasks...", count).toChat(player);

        long start = System.nanoTime();
        AtomicInteger remaining = new AtomicInteger(count);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger cancelled = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();

        for (int i = 0; i < count; i++) {
            AsyncTask<Integer> task = Async.supply(() -> 1 + 1);
            if (i % 10 == 0) {
                task.cancel();
            }
            task.onComplete(result -> {
                if (result.isSuccess()) {
                    succeeded.incrementAndGet();
                } else if (result.isCancelled()) {
                    cancelled.incrementAndGet();
                } else {
                    failed.incrementAndGet();
                }
                if (remaining.decrementAndGet() == 0) {
                    reportDone(player, count, start, succeeded.get(), cancelled.get(), failed.get());
                }
            });
        }
        return 1;
    }

    private static void reportDone(ServerPlayer player, int count, long startNanos, int succeeded, int cancelled, int failed) {
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
        Async.main(() -> EngineLog.channel("Async").success(
                "stresstest: {} tasks in {}ms — {} succeeded, {} cancelled, {} failed — live tasks now: {}",
                count, elapsedMs, succeeded, cancelled, failed, AsyncDebug.stats().liveTasks()
        ).toChat(player));
    }
}
