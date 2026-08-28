package com.dimalab.storymodengine.common.concurrent.example;

import com.dimalab.storymodengine.api.concurrent.CancellationToken;
import com.dimalab.storymodengine.common.concurrent.Async;
import com.dimalab.storymodengine.common.concurrent.AsyncTask;
import com.dimalab.storymodengine.common.flow.Flow;
import com.dimalab.storymodengine.common.logging.EngineLog;

import java.time.Duration;

/**
 * The request's own usage patterns, as real, compiled call sites — mirrors {@code
 * trigger.example.TriggerExamples}' role, except there's no {@code @AutoAsync} discovery to hang
 * these off (there's nothing to "register" — a task is a one-shot value, not a persistent
 * definition), so this is a plain, uninvoked reference gallery instead. Every method here is
 * intentionally never called by any bootstrap.
 */
public final class AsyncExamples {

    private AsyncExamples() {
    }

    /** {@code Async.run}/{@code Async.supply} — fire-and-forget and value-returning background work, both CPU by default. */
    static void basics() {
        Async.run(() -> EngineLog.channel("Async").info("background work ran"));
        AsyncTask<Integer> result = Async.supply(AsyncExamples::expensiveOperation);
        result.onSuccess(value -> EngineLog.channel("Async").info("got {}", value));
    }

    /** Chaining — {@code .thenRun}/{@code .thenApply}/{@code .thenAccept}, each inheriting the upstream executor. */
    static void chaining() {
        Async.run(() -> {
        }).thenRun(() -> EngineLog.channel("Async").info("ran, then ran again"));

        Async.supply(AsyncExamples::loadData)
                .thenApply(AsyncExamples::process)
                .thenAccept(processed -> EngineLog.channel("Async").info("processed: {}", processed));
    }

    /** Real-time delay — separate from Minecraft-tick scheduling ({@code Async.afterTicks}), never mixed. */
    static void delay() {
        Async.delay(Duration.ofSeconds(2), () -> EngineLog.channel("Async").info("two seconds later"));
    }

    /** IO work, then explicitly crossing back onto the main thread to touch anything Minecraft-shaped. */
    static void backgroundToMain() {
        Async.io().supply(AsyncExamples::readFromDisk)
                .thenApply(AsyncExamples::parse)
                .thenMain(parsed -> EngineLog.channel("Async").info("parsed {} on the main thread", parsed));
    }

    /** Starting on the main thread, moving heavy work off it, then returning — {@code Async.main()} is the zero-arg, value-returning form; {@code Async.main(Runnable)} is the void convenience. */
    static void mainToBackgroundToMain() {
        Async.main().supply(AsyncExamples::readMainThreadState)
                .thenAsync(AsyncExamples::heavyWork)
                .thenMain(processed -> EngineLog.channel("Async").info("back on main with {}", processed));
    }

    /** Success/failure/cancellation observers — no {@code CompletableFuture} ever crosses this boundary. */
    static void observers() {
        Async.supply(AsyncExamples::expensiveOperation)
                .onSuccess(value -> EngineLog.channel("Async").info("succeeded: {}", value))
                .onFailure(error -> EngineLog.channel("Async").error("failed: {}", error.getMessage()))
                .onCancel(() -> EngineLog.channel("Async").warn("cancelled"));
    }

    /** Cooperative cancellation — the body checks {@link CancellationToken#throwIfCancelled()} itself; nothing forces the thread to stop. */
    static void cooperativeCancellation() {
        CancellationToken token = com.dimalab.storymodengine.common.concurrent.cancel.CancellationSource.create().token();
        Async.run(token, () -> {
            while (true) {
                token.throwIfCancelled();
                // ... one unit of work per iteration ...
                break;
            }
        });
    }

    /** {@code Async.parallel}/{@code Async.race} and a hard timeout. */
    static void combinators() {
        AsyncTask<Integer> a = Async.supply(() -> 1);
        AsyncTask<Integer> b = Async.supply(() -> 2);
        Async.parallel(a, b).onSuccess(list -> EngineLog.channel("Async").info("all done: {}", list));

        AsyncTask<Integer> fast = Async.supply(() -> 1);
        AsyncTask<Integer> slow = Async.supply(AsyncExamples::expensiveOperation);
        Async.race(fast, slow).onSuccess(winner -> EngineLog.channel("Async").info("winner: {}", winner));

        Async.supply(AsyncExamples::expensiveOperation)
                .timeout(Duration.ofSeconds(5))
                .onFailure(error -> EngineLog.channel("Async").error("timed out or failed: {}", error.getMessage()));
    }

    /** {@code Flow.await} — the sanctioned way a Flow reaches into background work without ever polling or blocking the server thread. */
    static Flow questObjectiveThatFetchesRemoteData() {
        return Flow.await(
                ctx -> Async.io().supply(AsyncExamples::readFromDisk),
                (ctx, data) -> EngineLog.channel("Async").info("Flow resumed with {} for {}", data, ctx.player().getGameProfile().getName())
        );
    }

    private static int expensiveOperation() {
        return 42;
    }

    private static String loadData() {
        return "raw";
    }

    private static String process(String raw) {
        return raw.toUpperCase();
    }

    private static String readFromDisk() {
        return "disk-data";
    }

    private static String parse(String raw) {
        return raw + "-parsed";
    }

    private static boolean readMainThreadState() {
        return true;
    }

    private static String heavyWork(boolean flag) {
        return "computed:" + flag;
    }
}
