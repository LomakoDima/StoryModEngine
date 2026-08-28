package com.dimalab.storymodengine.api.concurrent;

/** Settles an {@code AsyncTask} as {@code FAILED} when it's submitted before the server has started or after it has begun stopping — never thrown at the call site, {@code Async.*} never throws. */
public final class AsyncShutdownException extends RuntimeException {

    public AsyncShutdownException(String message) {
        super(message);
    }
}
