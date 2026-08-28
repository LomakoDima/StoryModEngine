package com.dimalab.storymodengine.common.concurrent;

import com.dimalab.storymodengine.api.concurrent.CancellationToken;
import com.dimalab.storymodengine.api.concurrent.ExecutorKind;
import com.dimalab.storymodengine.api.event.Event;
import com.dimalab.storymodengine.api.event.EventPriority;
import com.dimalab.storymodengine.common.concurrent.cancel.CancellationSource;
import com.dimalab.storymodengine.common.concurrent.executor.AsyncExecutors;
import com.dimalab.storymodengine.common.concurrent.schedule.TickScheduler;
import com.dimalab.storymodengine.common.event.Events;
import com.dimalab.storymodengine.common.event.Subscription;
import com.dimalab.storymodengine.common.logging.EngineLog;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * The public entry point to this engine's concurrency layer — the only type most mod authors ever
 * import from {@code common.concurrent}. Mirrors the "one static facade" shape {@code Events}/
 * {@code Flow} already use. Every method here returns an {@link AsyncTask}; nothing here ever hands
 * back a raw {@code Thread}/{@code ExecutorService}/{@code Future}.
 *
 * <p>Background bodies passed to {@link #run}/{@link #supply}/{@link #io()}/{@link #cpu()} receive no
 * {@code Level}/{@code Entity}/{@code ServerPlayer}/{@code FlowContext} — there is nothing Minecraft-
 * shaped to accidentally touch off-thread. The only way back onto the main thread is {@link #main()}/
 * {@link #main(Runnable)}/{@link AsyncTask#thenMain}.
 */
public final class Async {

    private Async() {
    }

    // ---- executor scopes ----

    public static AsyncScope cpu() {
        return new AsyncScope(ExecutorKind.CPU);
    }

    public static AsyncScope io() {
        return new AsyncScope(ExecutorKind.IO);
    }

    public static AsyncScope scheduled() {
        return new AsyncScope(ExecutorKind.SCHEDULED);
    }

    public static AsyncScope main() {
        return new AsyncScope(ExecutorKind.MAIN);
    }

    /** Void convenience for the common case — equivalent to {@code main().run(body)}. See {@link AsyncTask} for why this can't overload with a value-returning form. */
    public static AsyncTask<Void> main(Runnable body) {
        return main().run(body);
    }

    // ---- CPU-default convenience (matches the request's Async.run/Async.supply) ----

    public static AsyncTask<Void> run(Runnable body) {
        return cpu().run(body);
    }

    public static AsyncTask<Void> run(CancellationToken token, Runnable body) {
        return cpu().run(token, body);
    }

    public static <T> AsyncTask<T> supply(Callable<T> body) {
        return cpu().supply(body);
    }

    public static <T> AsyncTask<T> supply(CancellationToken token, Callable<T> body) {
        return cpu().supply(token, body);
    }

    // ---- wall-clock scheduling — SCHEDULED times, CPU runs; never mixed with Minecraft ticks ----

    /** Runs {@code body} once, after {@code duration} of real time. */
    public static AsyncTask<Void> delay(Duration duration, Runnable body) {
        return delay(CancellationToken.NONE, duration, body);
    }

    public static AsyncTask<Void> delay(CancellationToken token, Duration duration, Runnable body) {
        return schedule(token, duration, () -> {
            body.run();
            return null;
        });
    }

    /** Runs {@code body} once, after {@code delay} of real time, on the CPU pool. */
    public static <T> AsyncTask<T> schedule(Duration delay, Callable<T> body) {
        return schedule(CancellationToken.NONE, delay, body);
    }

    public static <T> AsyncTask<T> schedule(CancellationToken token, Duration delay, Callable<T> body) {
        CancellationSource cancellation = CancellationSource.create();
        if (token != CancellationToken.NONE) {
            cancellation.linkTo(token);
        }
        CompletableFuture<T> future = new CompletableFuture<>();
        AsyncTask<T> handle = AsyncTask.adopt("schedule", ExecutorKind.SCHEDULED, cancellation, future);
        CancellationToken innerToken = cancellation.token();
        ScheduledFuture<?> job = AsyncExecutors.current().scheduled().schedule(() -> {
            if (innerToken.isCancelled()) {
                return;
            }
            AsyncExecutors.current().executorFor(ExecutorKind.CPU).execute(() -> runOnce(body, innerToken, future));
        }, delay.toMillis(), TimeUnit.MILLISECONDS);
        innerToken.onCancel(() -> job.cancel(false));
        return handle;
    }

    private static <T> void runOnce(Callable<T> body, CancellationToken token, CompletableFuture<T> future) {
        try {
            token.throwIfCancelled();
            future.complete(body.call());
        } catch (com.dimalab.storymodengine.api.concurrent.TaskCancelledException e) {
            future.cancel(false);
        } catch (Throwable t) {
            future.completeExceptionally(t);
        }
    }

    /** A repeating task, timed by the SCHEDULED pool but always run on CPU. The returned task never completes on its own — {@link AsyncTask#cancel()} is the only way it ends. A firing that throws is logged and does not stop later firings. */
    public static AsyncTask<Void> scheduleAtFixedRate(Duration initialDelay, Duration period, Runnable body) {
        return recurring("scheduleAtFixedRate", body, executor ->
                AsyncExecutors.current().scheduled().scheduleAtFixedRate(executor, initialDelay.toMillis(), period.toMillis(), TimeUnit.MILLISECONDS));
    }

    /** Like {@link #scheduleAtFixedRate}, but the next firing is timed from when the previous one finished rather than a fixed rate. */
    public static AsyncTask<Void> scheduleWithFixedDelay(Duration initialDelay, Duration delay, Runnable body) {
        return recurring("scheduleWithFixedDelay", body, executor ->
                AsyncExecutors.current().scheduled().scheduleWithFixedDelay(executor, initialDelay.toMillis(), delay.toMillis(), TimeUnit.MILLISECONDS));
    }

    private static AsyncTask<Void> recurring(String name, Runnable body, java.util.function.Function<Runnable, ScheduledFuture<?>> scheduler) {
        CancellationSource cancellation = CancellationSource.create();
        CompletableFuture<Void> future = new CompletableFuture<>();
        AsyncTask<Void> handle = AsyncTask.adopt(name, ExecutorKind.SCHEDULED, cancellation, future);
        CancellationToken token = cancellation.token();
        ScheduledFuture<?> job = scheduler.apply(() -> {
            if (token.isCancelled()) {
                return;
            }
            AsyncExecutors.current().executorFor(ExecutorKind.CPU).execute(() -> {
                if (token.isCancelled()) {
                    return;
                }
                try {
                    body.run();
                } catch (Throwable t) {
                    EngineLog.channel("Async").error("Recurring task '" + name + "' threw", t);
                }
            });
        });
        token.onCancel(() -> job.cancel(false));
        return handle;
    }

    // ---- Minecraft-tick scheduling — a separate axis from wall-clock time, always runs on MAIN ----

    public static AsyncTask<Void> nextTick(Runnable body) {
        return TickScheduler.after(1, body);
    }

    public static AsyncTask<Void> afterTicks(int ticks, Runnable body) {
        return TickScheduler.after(ticks, body);
    }

    public static AsyncTask<Void> everyTick(int periodTicks, Runnable body) {
        return TickScheduler.every(periodTicks, body);
    }

    // ---- combinators ----

    @SafeVarargs
    public static <T> AsyncTask<List<T>> parallel(AsyncTask<T>... tasks) {
        CancellationSource cancellation = CancellationSource.create();
        for (AsyncTask<T> task : tasks) {
            cancellation.token().onCancel(task::cancel);
        }
        CompletableFuture<?>[] raw = Arrays.stream(tasks).map(AsyncTask::toCompletableFuture).toArray(CompletableFuture[]::new);
        CompletableFuture<List<T>> combined = CompletableFuture.allOf(raw)
                .thenApply(ignored -> Arrays.stream(tasks).map(t -> t.toCompletableFuture().join()).toList());
        return AsyncTask.adopt("parallel", ExecutorKind.CPU, cancellation, combined);
    }

    /** The first of {@code tasks} to settle wins; every other task is cancelled once it does. */
    @SafeVarargs
    public static <T> AsyncTask<T> race(AsyncTask<T>... tasks) {
        CancellationSource cancellation = CancellationSource.create();
        CompletableFuture<?>[] raw = Arrays.stream(tasks).map(AsyncTask::toCompletableFuture).toArray(CompletableFuture[]::new);
        @SuppressWarnings("unchecked")
        CompletableFuture<T> combined = (CompletableFuture<T>) CompletableFuture.anyOf(raw);
        combined.whenComplete((value, throwable) -> {
            for (AsyncTask<T> task : tasks) {
                task.cancel();
            }
        });
        return AsyncTask.adopt("race", ExecutorKind.CPU, cancellation, combined);
    }

    public static <T> AsyncTask<T> timeout(AsyncTask<T> task, Duration duration) {
        return task.timeout(duration);
    }

    public static <T> AsyncTask<T> withTimeout(Duration duration, Callable<T> body) {
        return supply(body).timeout(duration);
    }

    public static <T> AsyncTask<T> deadline(Instant deadline, Callable<T> body) {
        return withTimeout(Duration.between(Instant.now(), deadline), body);
    }

    public static void cancel(AsyncTask<?> task) {
        task.cancel();
    }

    // ---- event integration — the existing EventBus, not a second one ----

    public static <E extends Event> AsyncTask<E> awaitEvent(Class<E> eventType) {
        return awaitEvent(eventType, e -> true, CancellationToken.NONE);
    }

    public static <E extends Event> AsyncTask<E> awaitEvent(Class<E> eventType, Predicate<E> filter) {
        return awaitEvent(eventType, filter, CancellationToken.NONE);
    }

    /** Completes the instant a matching event is posted — push-based through the existing {@code EventBus}, the same mechanism {@code EventWaiter} already uses, never a polling loop. */
    public static <E extends Event> AsyncTask<E> awaitEvent(Class<E> eventType, Predicate<E> filter, CancellationToken token) {
        CancellationSource cancellation = CancellationSource.create();
        if (token != CancellationToken.NONE) {
            cancellation.linkTo(token);
        }
        CompletableFuture<E> future = new CompletableFuture<>();
        AsyncTask<E> task = AsyncTask.adopt("awaitEvent:" + eventType.getSimpleName(), ExecutorKind.MAIN, cancellation, future);
        Subscription[] holder = new Subscription[1];
        holder[0] = Events.bus().subscribe(eventType, EventPriority.NORMAL, event -> {
            if (future.isDone() || !filter.test(event)) {
                return;
            }
            if (holder[0] != null) {
                holder[0].unsubscribe();
            }
            future.complete(event);
        });
        cancellation.token().onCancel(() -> {
            if (holder[0] != null) {
                holder[0].unsubscribe();
            }
        });
        return task;
    }

    // ---- debug ----

    public static AsyncStats debug() {
        return AsyncDebug.stats();
    }
}
