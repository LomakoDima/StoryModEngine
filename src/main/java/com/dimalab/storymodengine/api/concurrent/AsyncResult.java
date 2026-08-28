package com.dimalab.storymodengine.api.concurrent;

import java.util.Optional;

/**
 * The immutable terminal outcome of an {@code AsyncTask<T>} — what {@code onComplete(Consumer)}
 * receives, and what a {@code Flow.await} node hands to its result consumer on the main thread.
 * Not a future — a plain, safely-published value type (every field final), so it's always safe to
 * pass to any thread. Mirrors {@code RaycastResult}'s own "one type instead of switching on several
 * vanilla subclasses by hand" shape, one level more abstract (success/failure/cancellation instead
 * of block/entity/miss).
 */
public final class AsyncResult<T> {

    private final TaskState state;
    private final T value;
    private final Throwable error;

    private AsyncResult(TaskState state, T value, Throwable error) {
        this.state = state;
        this.value = value;
        this.error = error;
    }

    public static <T> AsyncResult<T> success(T value) {
        return new AsyncResult<>(TaskState.COMPLETED, value, null);
    }

    public static <T> AsyncResult<T> failure(Throwable error) {
        return new AsyncResult<>(TaskState.FAILED, null, error);
    }

    public static <T> AsyncResult<T> cancelled() {
        return new AsyncResult<>(TaskState.CANCELLED, null, null);
    }

    public TaskState state() {
        return state;
    }

    public boolean isSuccess() {
        return state == TaskState.COMPLETED;
    }

    public boolean isFailure() {
        return state == TaskState.FAILED;
    }

    public boolean isCancelled() {
        return state == TaskState.CANCELLED;
    }

    public Optional<T> value() {
        return Optional.ofNullable(value);
    }

    public Optional<Throwable> error() {
        return Optional.ofNullable(error);
    }

    @Override
    public String toString() {
        return switch (state) {
            case COMPLETED -> "AsyncResult{success=" + value + "}";
            case FAILED -> "AsyncResult{failure=" + error + "}";
            case CANCELLED -> "AsyncResult{cancelled}";
            default -> "AsyncResult{" + state + "}";
        };
    }
}
