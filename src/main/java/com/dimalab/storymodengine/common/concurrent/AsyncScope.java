package com.dimalab.storymodengine.common.concurrent;

import com.dimalab.storymodengine.api.concurrent.CancellationToken;
import com.dimalab.storymodengine.api.concurrent.ExecutorKind;
import com.dimalab.storymodengine.common.concurrent.executor.AsyncExecutors;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;

/**
 * What {@code Async.cpu()}/{@code Async.io()}/{@code Async.scheduled()}/{@code Async.main()} return —
 * one {@link ExecutorKind} bound in, so {@code Async.io().supply(...).thenApply(...).thenMain(...)}
 * reads left-to-right as "start here, end there." Obtained only from {@link Async}; never
 * constructed directly.
 *
 * <p>Submitting directly to the {@code SCHEDULED} scope ties up the engine's single timer thread for
 * the duration of the body — prefer {@link Async#delay} or {@link Async#schedule}, which hand the
 * body to {@code CPU} and use {@code SCHEDULED} only for timekeeping.
 */
public final class AsyncScope {

    private final ExecutorKind kind;

    AsyncScope(ExecutorKind kind) {
        this.kind = kind;
    }

    public ExecutorKind kind() {
        return kind;
    }

    public AsyncTask<Void> run(Runnable body) {
        return run(CancellationToken.NONE, body);
    }

    public AsyncTask<Void> run(CancellationToken token, Runnable body) {
        return AsyncTask.submit(kind, executor(), () -> {
            body.run();
            return null;
        }, token, "run@" + kind);
    }

    public <T> AsyncTask<T> supply(Callable<T> body) {
        return supply(CancellationToken.NONE, body);
    }

    public <T> AsyncTask<T> supply(CancellationToken token, Callable<T> body) {
        return AsyncTask.submit(kind, executor(), body, token, "supply@" + kind);
    }

    private Executor executor() {
        AsyncExecutors executors = AsyncExecutors.current();
        return switch (kind) {
            case CPU, IO -> executors.executorFor(kind);
            case SCHEDULED -> executors.scheduled();
            case MAIN -> executors.main();
        };
    }
}
