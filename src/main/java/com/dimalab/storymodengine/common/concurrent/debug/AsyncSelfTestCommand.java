package com.dimalab.storymodengine.common.concurrent.debug;

import com.dimalab.storymodengine.api.concurrent.TaskState;
import com.dimalab.storymodengine.api.concurrent.TaskTimeoutException;
import com.dimalab.storymodengine.common.StoryModEngine;
import com.dimalab.storymodengine.common.concurrent.Async;
import com.dimalab.storymodengine.common.concurrent.AsyncTask;
import com.dimalab.storymodengine.common.concurrent.StateFlow;
import com.dimalab.storymodengine.common.concurrent.cancel.CancellationSource;
import com.dimalab.storymodengine.common.concurrent.executor.AsyncExecutors;
import com.dimalab.storymodengine.common.concurrent.executor.PlatformThreadExecutorFactory;
import com.dimalab.storymodengine.common.event.Subscription;
import com.dimalab.storymodengine.common.flow.Flow;
import com.dimalab.storymodengine.common.flow.FlowHandle;
import com.dimalab.storymodengine.common.flow.FlowManager;
import com.dimalab.storymodengine.common.flow.registry.FlowRegistry;
import com.dimalab.storymodengine.common.logging.EngineLog;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * {@code /sme async selftest} — this project's usual synchronous, in-game
 * assert-and-report substitute for JUnit (see {@code trigger.debug.TriggerSelfTestCommand}) doesn't
 * fit here: several of these checks (the MAIN-dispatch ones especially) would deadlock a synchronous
 * command, since it would block the very server thread the continuation needs in order to run. So
 * this command only *starts* the suite — every check reports into a shared {@link
 * AsyncSelfTestReport}, which prints the final tally to chat once every check has reported in, or
 * once {@link #DEADLINE} elapses, whichever comes first.
 */
@Mod.EventBusSubscriber(modid = StoryModEngine.MODID)
public final class AsyncSelfTestCommand {

    private static final int TOTAL_CHECKS = 18;
    private static final Duration DEADLINE = Duration.ofSeconds(5);

    private AsyncSelfTestCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("sme")
                .then(Commands.literal("async")
                        .then(Commands.literal("selftest").executes(AsyncSelfTestCommand::run))));
    }

    private static int run(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();

        AtomicReference<AsyncSelfTestReport> holder = new AtomicReference<>();
        AsyncSelfTestReport report = new AsyncSelfTestReport(TOTAL_CHECKS, () -> reportResult(player, holder));
        holder.set(report);

        EngineLog.channel("Async").info("selftest: running {} checks (deadline {}s)...", TOTAL_CHECKS, DEADLINE.getSeconds()).toChat(player);
        Async.delay(DEADLINE, report::forceDone);

        checkBlockingGetGuard(report);
        checkSuccess(report);
        checkFailure(report);
        checkCancellation(report);
        checkTimeout(report);
        checkDelay(report);
        checkTickScheduling(report);
        checkParallel(report);
        checkRace(report);
        checkCancellationPropagation(report);
        checkExecutorShutdown(report);
        checkMainDispatch(report);
        checkBackgroundToMain(report);
        checkMainBackgroundMain(report);
        checkContinuationExecutorRule(report);
        checkFlowIntegration(player, report);
        checkFlowCancelPropagatesToAsync(player, report);
        checkStateFlow(report);

        return 1;
    }

    private static void reportResult(ServerPlayer player, AtomicReference<AsyncSelfTestReport> holder) {
        Async.main(() -> {
            AsyncSelfTestReport report = holder.get();
            List<String> failures = report.failures();
            if (failures.isEmpty()) {
                EngineLog.channel("Async").success("selftest: all {} checks passed", report.passedCount()).toChat(player);
            } else {
                EngineLog.channel("Async").error("selftest: {} passed, {} failed: {}",
                        report.passedCount(), failures.size(), String.join("; ", failures)).toChat(player);
            }
        });
    }

    // ---- checks ----

    private static void checkBlockingGetGuard(AsyncSelfTestReport report) {
        AsyncTask<Integer> task = Async.supply(() -> 1);
        boolean threw = false;
        try {
            task.blockingGet(Duration.ofSeconds(1));
        } catch (IllegalStateException e) {
            threw = true;
        }
        report.checkTrue("blockingGet: throws when called on the main thread", threw);
        report.finishOne();
    }

    private static void checkSuccess(AsyncSelfTestReport report) {
        Async.supply(() -> 42).onComplete(result -> {
            report.checkTrue("success: correct value observed", result.isSuccess() && Integer.valueOf(42).equals(result.value().orElse(null)));
            report.finishOne();
        });
    }

    private static void checkFailure(AsyncSelfTestReport report) {
        RuntimeException boom = new RuntimeException("selftest boom");
        Async.<Integer>supply(() -> {
            throw boom;
        }).thenApply(v -> v).onComplete(result -> {
            report.checkTrue("failure: reports failure", result.isFailure());
            report.checkTrue("failure: exception unwrapped to the same instance", result.error().map(e -> e == boom).orElse(false));
            report.finishOne();
        });
    }

    /**
     * {@link StateFlow} doesn't need any of the async/deadline machinery the rest of this file exists
     * for — every one of its behaviours is synchronous and observable immediately — but it lives right
     * alongside {@link Async}/{@link AsyncTask} in {@code common.concurrent}, so its check lives here
     * too rather than in a new, single-purpose command.
     */
    private static void checkStateFlow(AsyncSelfTestReport report) {
        StateFlow<String> flow = new StateFlow<>("initial");
        List<String> firstSeen = new ArrayList<>();
        Subscription first = flow.subscribe(firstSeen::add);
        report.checkTrue("StateFlow: subscribe replays the current value immediately", firstSeen.equals(List.of("initial")));

        flow.set("initial");
        report.checkTrue("StateFlow: set to an equal value does not notify", firstSeen.equals(List.of("initial")));

        flow.set("changed");
        report.checkTrue("StateFlow: set to a new value notifies existing subscribers", firstSeen.equals(List.of("initial", "changed")));
        report.checkTrue("StateFlow: value() reflects the latest set", "changed".equals(flow.value()));

        List<String> secondSeen = new ArrayList<>();
        flow.subscribe(secondSeen::add);
        report.checkTrue("StateFlow: a later subscriber is replayed the current value, not the initial one", secondSeen.equals(List.of("changed")));

        first.unsubscribe();
        flow.set("after-unsubscribe");
        report.checkTrue("StateFlow: unsubscribe stops further notifications", firstSeen.equals(List.of("initial", "changed")));
        report.checkTrue("StateFlow: a still-subscribed listener keeps receiving notifications", secondSeen.equals(List.of("changed", "after-unsubscribe")));

        report.finishOne();
    }

    private static void checkCancellation(AsyncSelfTestReport report) {
        CancellationSource source = CancellationSource.create();
        AtomicReference<Boolean> sawCancelled = new AtomicReference<>(false);
        AsyncTask<Void> task = Async.run(source.token(), AsyncSelfTestCommand::sleepTwoSeconds);
        task.onCancel(() -> sawCancelled.set(true));
        source.cancel();
        report.checkTrue("cancellation: task reaches CANCELLED", task.state() == TaskState.CANCELLED);
        report.checkTrue("cancellation: onCancel observer fired", sawCancelled.get());
        report.finishOne();
    }

    private static void checkTimeout(AsyncSelfTestReport report) {
        AsyncTask<Void> slow = Async.run(AsyncSelfTestCommand::sleepTwoSeconds);
        slow.timeout(Duration.ofMillis(200)).onComplete(result -> {
            report.checkTrue("timeout: fails with TaskTimeoutException",
                    result.isFailure() && result.error().map(e -> e instanceof TaskTimeoutException).orElse(false));
            report.checkTrue("timeout: upstream is cancelled", slow.isCancelled());
            report.finishOne();
        });
    }

    private static void checkDelay(AsyncSelfTestReport report) {
        long start = System.nanoTime();
        Async.delay(Duration.ofMillis(150), () -> {
        }).onComplete(result -> {
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            report.checkTrue("delay: fires no earlier than requested", result.isSuccess() && elapsedMs >= 140);
            report.finishOne();
        });
    }

    private static void checkTickScheduling(AsyncSelfTestReport report) {
        Async.afterTicks(2, () -> {
        }).onComplete(result -> {
            report.checkTrue("tick scheduling: afterTicks fires", result.isSuccess());
            report.finishOne();
        });
    }

    private static void checkParallel(AsyncSelfTestReport report) {
        AsyncTask<Integer> a = Async.supply(() -> 1);
        AsyncTask<Integer> b = Async.supply(() -> 2);
        AsyncTask<Integer> c = Async.supply(() -> 3);
        Async.parallel(a, b, c).onComplete(result -> {
            report.checkTrue("parallel: all results collected in order",
                    result.isSuccess() && result.value().map(list -> list.equals(List.of(1, 2, 3))).orElse(false));
            report.finishOne();
        });
    }

    private static void checkRace(AsyncSelfTestReport report) {
        AsyncTask<String> fast = Async.supply(() -> {
            sleepMillis(20);
            return "fast";
        });
        AsyncTask<String> slow = Async.supply(() -> {
            sleepMillis(500);
            return "slow";
        });
        Async.race(fast, slow).onComplete(result -> {
            report.checkTrue("race: fastest task wins", result.isSuccess() && "fast".equals(result.value().orElse(null)));
            report.finishOne();
        });
    }

    private static void checkCancellationPropagation(AsyncSelfTestReport report) {
        AsyncTask<Integer> parent = Async.supply(() -> {
            sleepMillis(1000);
            return 1;
        });
        AsyncTask<Integer> child = parent.thenApply(v -> v + 1);
        parent.cancel();
        report.checkTrue("cancellation propagation: parent cancel reaches child", child.isCancelled());

        AsyncTask<Integer> parent2 = Async.supply(() -> {
            sleepMillis(1000);
            return 1;
        });
        AsyncTask<Integer> child2 = parent2.thenApply(v -> v + 1);
        child2.cancel();
        report.checkTrue("cancellation propagation: child cancel does not reach parent", !parent2.isCancelled());
        parent2.cancel();
        report.finishOne();
    }

    private static void checkExecutorShutdown(AsyncSelfTestReport report) {
        AsyncExecutors throwaway = AsyncExecutors.createForTest(new PlatformThreadExecutorFactory());
        report.checkTrue("executor shutdown: starts RUNNING", throwaway.isAccepting());
        throwaway.shutdown();
        report.checkTrue("executor shutdown: reaches TERMINATED", throwaway.state() == AsyncExecutors.State.TERMINATED);
        report.finishOne();
    }

    private static void checkMainDispatch(AsyncSelfTestReport report) {
        Async.main(() -> {
            report.checkTrue("MAIN dispatch: body observes the main thread", AsyncExecutors.current().main().isMainThread());
            report.finishOne();
        });
    }

    private static void checkBackgroundToMain(AsyncSelfTestReport report) {
        Async.cpu().supply(() -> Thread.currentThread().getName())
                .thenMain(name -> {
                    report.checkTrue("BACKGROUND->MAIN: body started off-main", name.startsWith("SME-Async-CPU-"));
                    report.checkTrue("BACKGROUND->MAIN: thenMain observes the main thread", AsyncExecutors.current().main().isMainThread());
                    report.finishOne();
                });
    }

    private static void checkMainBackgroundMain(AsyncSelfTestReport report) {
        Async.main().supply(() -> AsyncExecutors.current().main().isMainThread())
                .thenAsync(wasMain -> wasMain && !AsyncExecutors.current().main().isMainThread())
                .thenMain(ok -> {
                    report.checkTrue("MAIN->BACKGROUND->MAIN: round trip correct",
                            Boolean.TRUE.equals(ok) && AsyncExecutors.current().main().isMainThread());
                    report.finishOne();
                });
    }

    private static void checkContinuationExecutorRule(AsyncSelfTestReport report) {
        Async.io().supply(() -> Thread.currentThread().getName())
                .thenApply(ignoredFirstStageThreadName -> Thread.currentThread().getName())
                .onComplete(result -> {
                    report.checkTrue("continuation-executor rule: thenApply stays on IO",
                            result.isSuccess() && result.value().map(name -> name.startsWith("SME-Async-IO-")).orElse(false));
                    report.finishOne();
                });
    }

    private static void checkFlowIntegration(ServerPlayer player, AsyncSelfTestReport report) {
        ResourceLocation id = new ResourceLocation(StoryModEngine.MODID, "__async_selftest_await__");
        AtomicReference<Integer> captured = new AtomicReference<>();
        // Wrapped in Flow.sequence(...) rather than registered as a bare AsyncNode root — see
        // AsyncNode's Javadoc: a bare-root leaf that completes off-stack between ticks hits a
        // pre-existing, separately-scoped FlowManager.tick() limitation (before/after state is
        // re-read fresh every tick, not carried across ticks), so it's never used standalone. A real
        // Flow.await(...) step is virtually always part of a larger sequence anyway.
        Flow flow = Flow.sequence(Flow.await(ctx -> Async.supply(() -> 21 * 2), (ctx, value) -> captured.set(value)));
        FlowRegistry.register(id, flow);
        FlowHandle handle = FlowManager.start(id, player);
        Async.delay(Duration.ofMillis(300), () -> {
        }).thenMain(ignored -> {
            report.checkTrue("Flow+Async integration: flow completes", handle.isCompleted());
            report.checkTrue("Flow+Async integration: consumer received the result on the main thread",
                    Integer.valueOf(42).equals(captured.get()));
            report.finishOne();
        });
    }

    private static void checkFlowCancelPropagatesToAsync(ServerPlayer player, AsyncSelfTestReport report) {
        ResourceLocation id = new ResourceLocation(StoryModEngine.MODID, "__async_selftest_cancel__");
        AtomicReference<AsyncTask<Void>> capturedTask = new AtomicReference<>();
        Flow flow = Flow.sequence(Flow.async(ctx -> {
            AsyncTask<Void> task = Async.run(AsyncSelfTestCommand::sleepTwoSeconds);
            capturedTask.set(task);
            return task;
        }));
        FlowRegistry.register(id, flow);
        FlowHandle handle = FlowManager.start(id, player);
        handle.cancel();
        AsyncTask<Void> task = capturedTask.get();
        report.checkTrue("Flow-cancel -> async-cancel: task reaches CANCELLED", task != null && task.isCancelled());
        report.checkTrue("Flow-cancel -> async-cancel: flow node reaches CANCELLED", handle.isCancelled());
        report.finishOne();
    }

    private static void sleepTwoSeconds() {
        sleepMillis(2000);
    }

    private static void sleepMillis(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
