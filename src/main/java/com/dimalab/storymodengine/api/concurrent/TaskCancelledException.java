package com.dimalab.storymodengine.api.concurrent;

/** Thrown by {@link CancellationToken#throwIfCancelled()} — an unchecked signal, never a real error, caught internally to settle the task as {@code CANCELLED} rather than {@code FAILED}. */
public final class TaskCancelledException extends RuntimeException {

    public TaskCancelledException() {
        super("Task was cancelled", null, false, false);
    }
}
