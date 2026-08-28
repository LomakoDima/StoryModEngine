package com.dimalab.storymodengine.common.concurrent.executor;

import com.dimalab.storymodengine.api.concurrent.ExecutorKind;
import com.dimalab.storymodengine.common.logging.EngineLog;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Names threads {@code SME-Async-<Kind>-N} for thread-dump observability, marks every one a daemon
 * (so a wedged pool can never keep the JVM alive), and logs an uncaught exception instead of letting
 * it vanish — the last-resort backstop; every task body is also wrapped at submission time (see
 * {@code Async}), so this should never actually fire in normal operation.
 */
public final class AsyncThreadFactory implements ThreadFactory {

    private final String prefix;
    private final AtomicInteger counter = new AtomicInteger(1);

    public AsyncThreadFactory(ExecutorKind kind) {
        this.prefix = "SME-Async-" + kind.name() + "-";
    }

    @Override
    public Thread newThread(Runnable runnable) {
        Thread thread = new Thread(runnable, prefix + counter.getAndIncrement());
        thread.setDaemon(true);
        thread.setUncaughtExceptionHandler((t, e) ->
                EngineLog.channel("Async").error("Uncaught exception on " + t.getName(), e));
        return thread;
    }
}
