package com.dimalab.storymodengine.api.concurrent;

/** Settles an {@code AsyncTask} as {@code FAILED} when its {@code .timeout(Duration)} elapses before the task itself completes — the source task is separately cancelled. */
public final class TaskTimeoutException extends RuntimeException {

    public TaskTimeoutException(String message) {
        super(message);
    }
}
