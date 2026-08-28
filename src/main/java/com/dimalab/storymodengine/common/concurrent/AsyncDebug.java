package com.dimalab.storymodengine.common.concurrent;

import com.dimalab.storymodengine.api.concurrent.ExecutorKind;
import com.dimalab.storymodengine.common.concurrent.executor.AsyncExecutors;
import com.dimalab.storymodengine.common.concurrent.integration.FlowResumeQueue;
import com.dimalab.storymodengine.common.concurrent.schedule.TickScheduler;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Optional debug/observability surface — what {@code Async.debug()} returns. The live-task count and
 * per-kind counters {@link #stats()} reports are always maintained (cheap, needed anyway for the
 * shutdown sweep in {@code executor.AsyncExecutors}); the settled-task history {@link #history()}
 * reads is opt-in via {@link #setEnabled} (default {@code false}), so debugging is never a mandatory
 * part of the runtime.
 */
public final class AsyncDebug {

    private static volatile boolean enabled = false;

    private AsyncDebug() {
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    /** Every task currently running or queued, right now. */
    public static List<TaskInfo> live() {
        return AsyncRegistry.liveTasks().stream().map(AsyncTask::toInfo).toList();
    }

    /** Settled tasks, oldest first, bounded to the last 128 — empty unless {@link #setEnabled} is on. */
    public static List<TaskInfo> history() {
        return AsyncRegistry.history();
    }

    /** A live or settled task by id, or {@code null} if neither (never existed, or settled with history off). */
    public static TaskInfo find(long id) {
        AsyncTask<?> live = AsyncRegistry.find(id);
        if (live != null) {
            return live.toInfo();
        }
        return AsyncRegistry.findInHistory(id);
    }

    public static AsyncStats stats() {
        Map<ExecutorKind, AsyncStats.KindStats> byKind = new EnumMap<>(ExecutorKind.class);
        AsyncExecutors executors = AsyncExecutors.current();
        for (ExecutorKind kind : ExecutorKind.values()) {
            long submitted = AsyncRegistry.submitted().get(kind).sum();
            long completed = AsyncRegistry.completed().get(kind).sum();
            long failed = AsyncRegistry.failed().get(kind).sum();
            long cancelled = AsyncRegistry.cancelled().get(kind).sum();
            long rejected = AsyncRegistry.rejected().get(kind).sum();
            int poolSize = 0;
            int active = 0;
            int queued = 0;
            if ((kind == ExecutorKind.CPU || kind == ExecutorKind.IO) && executors.isAccepting()
                    && executors.executorFor(kind) instanceof ThreadPoolExecutor pool) {
                // Only a ThreadPoolExecutor (today's platform-thread backend) exposes pool gauges — a
                // future Virtual Thread backend simply reports zeros here, which is correct: it has no
                // fixed pool size to report.
                poolSize = pool.getPoolSize();
                active = pool.getActiveCount();
                queued = pool.getQueue().size();
            }
            byKind.put(kind, new AsyncStats.KindStats(submitted, completed, failed, cancelled, rejected, poolSize, active, queued));
        }
        return new AsyncStats(byKind, AsyncRegistry.liveCount(), FlowResumeQueue.size(), TickScheduler.size(), enabled, AsyncRegistry.historySize());
    }
}
