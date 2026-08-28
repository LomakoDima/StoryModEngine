package com.dimalab.storymodengine.common.concurrent.executor;

import com.dimalab.storymodengine.api.concurrent.ExecutorKind;

import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * The one and only {@link ExecutorFactory} implementation today — plain {@code java.lang.Thread}
 * pools, since this project targets Java 17 and Virtual Threads (Java 21+) are explicitly out of
 * scope for this pass. See {@link ExecutorFactory}'s own doc for the seam a future {@code
 * VirtualThreadExecutorFactory} would fill without touching anything else.
 */
public final class PlatformThreadExecutorFactory implements ExecutorFactory {

    /** CPU pool size: leaves one core free for the Minecraft server thread itself. */
    @Override
    public ThreadPoolExecutor createCpu() {
        int threads = Math.max(2, Runtime.getRuntime().availableProcessors() - 1);
        return new ThreadPoolExecutor(threads, threads, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(), new AsyncThreadFactory(ExecutorKind.CPU));
    }

    /** Elastic, hard-capped at 64 — a cached-pool shape (idle threads die after 60s) so blocking I/O work doesn't starve for threads, with a real ceiling so nothing proliferates without bound. */
    @Override
    public ThreadPoolExecutor createIo() {
        return new ThreadPoolExecutor(0, 64, 60L, TimeUnit.SECONDS,
                new SynchronousQueue<>(), new AsyncThreadFactory(ExecutorKind.IO));
    }

    /** Exactly one thread — timekeeping only, see {@code Async}'s own doc on why SCHEDULED never runs user task bodies. */
    @Override
    public ScheduledExecutorService createScheduled() {
        return Executors.newScheduledThreadPool(1, new AsyncThreadFactory(ExecutorKind.SCHEDULED));
    }

    @Override
    public String describe() {
        return "platform threads";
    }
}
