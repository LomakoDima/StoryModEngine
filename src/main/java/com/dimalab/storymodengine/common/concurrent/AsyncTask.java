package com.dimalab.storymodengine.common.concurrent;

import com.dimalab.storymodengine.api.concurrent.AsyncResult;
import com.dimalab.storymodengine.api.concurrent.AsyncShutdownException;
import com.dimalab.storymodengine.api.concurrent.CancellationToken;
import com.dimalab.storymodengine.api.concurrent.ExecutorKind;
import com.dimalab.storymodengine.api.concurrent.TaskCancelledException;
import com.dimalab.storymodengine.api.concurrent.TaskState;
import com.dimalab.storymodengine.api.concurrent.TaskTimeoutException;
import com.dimalab.storymodengine.common.concurrent.cancel.CancellationSource;
import com.dimalab.storymodengine.common.concurrent.executor.AsyncExecutors;
import com.dimalab.storymodengine.common.logging.EngineLog;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The one public type a mod author holds — future, handle, and composition point in one, the same
 * "one type plays every role" shape {@code Trigger} already uses for facade+definition. Wraps a
 * private {@link CompletableFuture} and never exposes it as the primary surface: no {@code join()},
 * no {@code get()} anywhere in this class. The one escape hatch, {@link #toCompletableFuture()},
 * matches the "raw escape hatch" pattern {@code RaycastResult#raw()} already establishes; the other,
 * {@link #blockingGet(Duration)}, actively throws if called on the Minecraft main thread — the
 * "never block main thread" rule is an enforced invariant here, not just a Javadoc warning.
 *
 * <p><b>Continuation-executor rule</b>: {@link #thenApply}/{@link #thenAccept}/{@link #thenRun} run
 * on the exact same {@link ExecutorKind} the upstream stage completed on — a chain stays where it
 * started until {@link #thenMain}/{@link #thenAsync}/{@link #thenIo} explicitly move it. This engine
 * never calls a plain (non-executor) {@code CompletableFuture} continuation method internally,
 * specifically to avoid ever landing work on {@code ForkJoinPool.commonPool()} — the exact
 * uncontrolled thread pool this whole subsystem exists to replace.
 */
public final class AsyncTask<T> {

    private final long id;
    private final String name;
    private final ExecutorKind kind;
    private final Long parentId;
    private final long createdAtNanos;
    private final CancellationSource cancellation;
    private final CompletableFuture<T> future;

    private volatile TaskState state = TaskState.PENDING;
    private volatile Long startedAtNanos;
    private volatile Long finishedAtNanos;
    private volatile boolean failureObserved;
    private volatile String errorSummary;

    private AsyncTask(String name, ExecutorKind kind, Long parentId, CancellationSource cancellation, CompletableFuture<T> future) {
        this.id = AsyncRegistry.nextId();
        this.name = name;
        this.kind = kind;
        this.parentId = parentId;
        this.createdAtNanos = System.nanoTime();
        this.cancellation = cancellation;
        this.future = future;
        cancellation.token().onCancel(() -> future.cancel(false));
        future.whenComplete(this::onSettled);
        AsyncRegistry.register(this);
    }

    // ---- construction (package-private — only Async/AsyncScope build tasks) ----

    /** The root submission — runs {@code body} on {@code executor}, honoring {@code externalToken} cooperatively. Settles immediately with {@link AsyncShutdownException} if the engine isn't accepting work. */
    static <T> AsyncTask<T> submit(ExecutorKind kind, Executor executor, Callable<T> body, CancellationToken externalToken, String name) {
        CancellationSource cancellation = CancellationSource.create();
        if (externalToken != null) {
            cancellation.linkTo(externalToken);
        }
        CompletableFuture<T> future = new CompletableFuture<>();
        AsyncTask<T> task = new AsyncTask<>(name, kind, null, cancellation, future);

        if (!AsyncExecutors.current().isAccepting()) {
            AsyncRegistry.recordRejected(kind);
            future.completeExceptionally(new AsyncShutdownException("Rejected — engine concurrency is not accepting new work"));
            return task;
        }

        CancellationToken token = cancellation.token();
        try {
            executor.execute(() -> task.runBody(body, token));
        } catch (java.util.concurrent.RejectedExecutionException e) {
            AsyncRegistry.recordRejected(kind);
            future.completeExceptionally(new AsyncShutdownException("Rejected by executor — shutting down"));
        }
        return task;
    }

    /**
     * Adopts an already-driven {@link CompletableFuture} as a debuggable, cancellable task. Public
     * only because callers span packages (e.g. {@code schedule.TickScheduler}) — used by {@code
     * Async}'s own scheduling/combinator methods, which complete the future themselves rather than
     * running a body through {@link #submit}; not intended for mod-author code.
     */
    public static <T> AsyncTask<T> adopt(String name, ExecutorKind kind, CancellationSource cancellation, CompletableFuture<T> future) {
        return new AsyncTask<>(name, kind, null, cancellation, future);
    }

    private void runBody(Callable<T> body, CancellationToken token) {
        if (state == TaskState.CANCELLED || future.isDone()) {
            return;
        }
        startedAtNanos = System.nanoTime();
        state = TaskState.RUNNING;
        try {
            token.throwIfCancelled();
            T result = body.call();
            future.complete(result);
        } catch (TaskCancelledException e) {
            future.cancel(false);
        } catch (Throwable t) {
            future.completeExceptionally(t);
        }
    }

    private void onSettled(T value, Throwable throwable) {
        finishedAtNanos = System.nanoTime();
        TaskState terminal;
        if (isEffectivelyCancelled(throwable)) {
            terminal = TaskState.CANCELLED;
        } else if (throwable != null) {
            terminal = TaskState.FAILED;
            Throwable real = unwrap(throwable);
            errorSummary = real.getClass().getSimpleName() + ": " + real.getMessage();
            scheduleUnobservedFailureCheck(real);
        } else {
            terminal = TaskState.COMPLETED;
        }
        state = terminal;
        AsyncRegistry.unregister(this, terminal);
    }

    /**
     * Whether this settlement should be treated as CANCELLED rather than FAILED. A direct {@code
     * future.cancel(false)} on *this* stage makes {@code future.isCancelled()} true, but a stage
     * whose upstream was cancelled instead settles via ordinary exceptional propagation — the
     * completing exception is a bare {@link java.util.concurrent.CancellationException} (sometimes
     * unwrapped from a {@link CompletionException}), not a JDK-recognized "this future was
     * cancelled." Both cases mean the same thing to this engine's {@link TaskState}.
     */
    private boolean isEffectivelyCancelled(Throwable throwable) {
        return future.isCancelled() || unwrap(throwable) instanceof java.util.concurrent.CancellationException;
    }

    /**
     * A failure logged the instant it settles could be a false positive: {@link #onFailure}/{@link
     * #onComplete}/a {@code .thenX} continuation is very often attached on the calling thread
     * *immediately after* this task is created, but that attachment can't happen before this task's
     * own body (on another thread entirely) has already run and settled — a body that fails fast
     * (or is cancelled before it ever starts) can easily beat the caller's very next statement. A
     * short grace window on the SCHEDULED pool, re-checking {@link #failureObserved}, closes that
     * race for every realistic call shape (chaining/observing always happens synchronously, right
     * after construction) without needing a callback-count reference scheme.
     */
    private void scheduleUnobservedFailureCheck(Throwable real) {
        try {
            AsyncExecutors.current().scheduled().schedule(() -> {
                if (!failureObserved) {
                    EngineLog.channel("Async").error("Unobserved failure in async task '" + name + "'", real);
                }
            }, 50, TimeUnit.MILLISECONDS);
        } catch (RuntimeException e) {
            if (!failureObserved) {
                EngineLog.channel("Async").error("Unobserved failure in async task '" + name + "'", real);
            }
        }
    }

    private static Throwable unwrap(Throwable t) {
        if (t instanceof CompletionException && t.getCause() != null) {
            return t.getCause();
        }
        return t;
    }

    // ---- identity / debug ----

    public long id() {
        return id;
    }

    public String name() {
        return name;
    }

    public ExecutorKind kind() {
        return kind;
    }

    public TaskState state() {
        return state;
    }

    public boolean isDone() {
        return future.isDone();
    }

    public boolean isCancelled() {
        return state == TaskState.CANCELLED;
    }

    TaskInfo toInfo() {
        return new TaskInfo(id, name, kind, state, parentId, createdAtNanos, startedAtNanos, finishedAtNanos, errorSummary);
    }

    // ---- cancellation ----

    /** Cancels this task and every task derived from it. Never cancels the task this one was itself derived from — an upstream may be shared (e.g. by {@code Async.parallel}/{@code Async.race}). Cooperative only — never interrupts a running thread. */
    public void cancel() {
        cancellation.cancel();
    }

    // ---- continuations — all inherit this task's ExecutorKind unless named otherwise ----

    public AsyncTask<Void> thenRun(Runnable action) {
        return derive("thenRun", executorFor(kind), ignored -> {
            action.run();
            return null;
        });
    }

    public <U> AsyncTask<U> thenApply(Function<T, U> fn) {
        return derive("thenApply", executorFor(kind), fn);
    }

    public AsyncTask<Void> thenAccept(Consumer<T> action) {
        return derive("thenAccept", executorFor(kind), value -> {
            action.accept(value);
            return null;
        });
    }

    /** Explicitly moves onto the Minecraft main thread — the only place Minecraft/Forge API may be touched. */
    public AsyncTask<Void> thenMain(Consumer<T> action) {
        return derive("thenMain", AsyncExecutors.current().main(), value -> {
            action.accept(value);
            return null;
        }, ExecutorKind.MAIN);
    }

    /** Explicitly moves onto the CPU pool — "get me off wherever I am." */
    public <U> AsyncTask<U> thenAsync(Function<T, U> fn) {
        return derive("thenAsync", executorFor(ExecutorKind.CPU), fn, ExecutorKind.CPU);
    }

    /** Explicitly moves onto the IO pool. */
    public <U> AsyncTask<U> thenIo(Function<T, U> fn) {
        return derive("thenIo", executorFor(ExecutorKind.IO), fn, ExecutorKind.IO);
    }

    private <U> AsyncTask<U> derive(String stageName, Executor executor, Function<T, U> fn) {
        return derive(stageName, executor, fn, kind);
    }

    private <U> AsyncTask<U> derive(String stageName, Executor executor, Function<T, U> fn, ExecutorKind stageKind) {
        failureObserved = true;
        CancellationSource childCancellation = cancellation.createChild();
        CompletableFuture<U> derived = future.thenApplyAsync(value -> {
            childCancellation.token().throwIfCancelled();
            return fn.apply(value);
        }, executor);
        return new AsyncTask<>(name + "." + stageName, stageKind, id, childCancellation, derived);
    }

    // ---- observers — attach without producing a new stage; return this ----

    public AsyncTask<T> onSuccess(Consumer<T> action) {
        failureObserved = true;
        future.whenComplete((value, throwable) -> {
            if (throwable == null && !future.isCancelled()) {
                action.accept(value);
            }
        });
        return this;
    }

    public AsyncTask<T> onFailure(Consumer<Throwable> action) {
        failureObserved = true;
        future.whenComplete((value, throwable) -> {
            if (throwable != null && !isEffectivelyCancelled(throwable)) {
                action.accept(unwrap(throwable));
            }
        });
        return this;
    }

    public AsyncTask<T> onCancel(Runnable action) {
        failureObserved = true;
        future.whenComplete((value, throwable) -> {
            if (isEffectivelyCancelled(throwable)) {
                action.run();
            }
        });
        return this;
    }

    /** Fires exactly once, with the terminal outcome regardless of shape — see {@link AsyncResult}. */
    public AsyncTask<T> onComplete(Consumer<AsyncResult<T>> action) {
        failureObserved = true;
        future.whenComplete((value, throwable) -> action.accept(toResult(value, throwable)));
        return this;
    }

    private AsyncResult<T> toResult(T value, Throwable throwable) {
        if (isEffectivelyCancelled(throwable)) {
            return AsyncResult.cancelled();
        }
        if (throwable != null) {
            return AsyncResult.failure(unwrap(throwable));
        }
        return AsyncResult.success(value);
    }

    // ---- timeout ----

    /**
     * A derived task that fails with {@link TaskTimeoutException} if {@code duration} elapses before
     * this one settles — this task is then cancelled. Timekeeping runs on the SCHEDULED pool; the
     * timeout check itself does no work.
     *
     * <p>Cancels the upstream <em>before</em> resolving the returned task, deliberately — {@code
     * CompletableFuture#completeExceptionally} runs every dependent callback (including whatever a
     * caller attached via {@code .onComplete}/{@code .onFailure}) synchronously, before it returns.
     * Resolving first and cancelling second would let a caller observe the timeout while {@code
     * this.isCancelled()} was still momentarily false. The {@code timedOut} guard exists because
     * cancelling the upstream here re-triggers this same {@code future.whenComplete} below (it reacts
     * to any settlement of {@code future}, cancellation included) — without it, that second entry
     * would try to resolve {@code combined} a second time from the wrong outcome.
     *
     * <p>The returned task deliberately does <em>not</em> use {@code cancellation.createChild()} —
     * a child would cascade the upstream's own cancellation (including the one this method performs
     * internally, right above, the instant a timeout fires) straight back onto {@code combined},
     * racing the very {@code completeExceptionally(TaskTimeoutException)} call on the next line and
     * usually winning it, so the timeout was silently reported as a plain cancellation instead
     * (caught by {@code AsyncSelfTestCommand} actually failing this exact check). Instead it gets an
     * independent source, one-way-linked so cancelling *it* still cancels the upstream — the outward
     * behavior callers expect — without the upstream's own cancellation cascading back in.
     */
    public AsyncTask<T> timeout(Duration duration) {
        failureObserved = true;
        CompletableFuture<T> combined = new CompletableFuture<>();
        java.util.concurrent.atomic.AtomicBoolean timedOut = new java.util.concurrent.atomic.AtomicBoolean(false);
        CancellationSource combinedCancellation = CancellationSource.create();
        combinedCancellation.token().onCancel(this::cancel);
        ScheduledFuture<?> timeoutJob = AsyncExecutors.current().scheduled().schedule(() -> {
            if (!future.isDone() && timedOut.compareAndSet(false, true)) {
                cancel();
                combined.completeExceptionally(new TaskTimeoutException("Timed out after " + duration));
            }
        }, duration.toMillis(), TimeUnit.MILLISECONDS);
        future.whenComplete((value, throwable) -> {
            timeoutJob.cancel(false);
            if (timedOut.get()) {
                return;
            }
            if (isEffectivelyCancelled(throwable)) {
                combined.cancel(false);
            } else if (throwable != null) {
                combined.completeExceptionally(throwable);
            } else {
                combined.complete(value);
            }
        });
        return new AsyncTask<>(name + ".timeout", kind, id, combinedCancellation, combined);
    }

    // ---- escape hatches ----

    /** The underlying future, for advanced callers — never used internally by anything in this package. */
    public CompletableFuture<T> toCompletableFuture() {
        return future;
    }

    /**
     * Blocks the calling thread until this task settles, or {@code timeout} elapses. <b>Throws
     * {@link IllegalStateException} if called on the Minecraft main thread</b> — the one place this
     * engine actively prevents the exact mistake the whole design exists to avoid, rather than only
     * documenting it. Intended for genuinely thread-agnostic callers (e.g. a self-test), never for
     * gameplay code.
     */
    public T blockingGet(Duration timeout) {
        if (AsyncExecutors.current().main().isMainThread()) {
            throw new IllegalStateException("blockingGet() must never be called on the Minecraft main thread — use thenMain(...)/await instead");
        }
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (java.util.concurrent.ExecutionException e) {
            throw new RuntimeException(unwrap(e.getCause() != null ? e.getCause() : e));
        } catch (java.util.concurrent.TimeoutException e) {
            throw new TaskTimeoutException("blockingGet timed out after " + timeout);
        }
    }

    private static Executor executorFor(ExecutorKind kind) {
        AsyncExecutors executors = AsyncExecutors.current();
        return switch (kind) {
            case CPU, IO -> executors.executorFor(kind);
            case SCHEDULED -> executors.scheduled();
            case MAIN -> executors.main();
        };
    }

    @Override
    public String toString() {
        return "AsyncTask{#" + id + " " + name + " kind=" + kind + " state=" + state + "}";
    }
}
