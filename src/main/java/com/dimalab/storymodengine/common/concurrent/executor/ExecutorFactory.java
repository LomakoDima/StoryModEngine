package com.dimalab.storymodengine.common.concurrent.executor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * The Java 21 seam. This is the entire abstraction {@code common.concurrent} needs to one day gain a
 * {@code VirtualThreadExecutor} backend without changing {@code Async}, {@code AsyncTask}, or any
 * call site — a second implementation of this interface, nothing else. Exactly one implementation
 * exists today, {@link PlatformThreadExecutorFactory}; no Java 21 API is referenced anywhere in this
 * package.
 */
public interface ExecutorFactory {

    ExecutorService createCpu();

    ExecutorService createIo();

    ScheduledExecutorService createScheduled();

    /** A short label for the "executors started" log line, e.g. {@code "platform threads"}. */
    String describe();
}
