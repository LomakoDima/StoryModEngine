package com.dimalab.storymodengine.common.concurrent;

import com.dimalab.storymodengine.api.concurrent.ExecutorKind;
import com.dimalab.storymodengine.api.concurrent.TaskState;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Package-private bookkeeping — {@link AsyncDebug} is the public read surface over this, the same
 * "internal registry, public facade" split {@code trigger.TriggerFiredTracker}/{@code TriggerSystem}
 * already use. The live-task map and per-kind counters are always maintained (needed anyway for the
 * shutdown sweep — see {@code executor.AsyncExecutors#shutdown}); the settled-task history is the
 * one part that's genuinely optional (see {@link AsyncDebug#setEnabled}).
 */
final class AsyncRegistry {

    private static final Map<Long, AsyncTask<?>> LIVE = new ConcurrentHashMap<>();
    private static final AtomicLong NEXT_ID = new AtomicLong(1);

    private static final int HISTORY_LIMIT = 128;
    private static final ConcurrentLinkedDeque<TaskInfo> HISTORY = new ConcurrentLinkedDeque<>();
    private static final AtomicInteger HISTORY_SIZE = new AtomicInteger();

    private static final Map<ExecutorKind, LongAdder> SUBMITTED = countersFor();
    private static final Map<ExecutorKind, LongAdder> COMPLETED = countersFor();
    private static final Map<ExecutorKind, LongAdder> FAILED = countersFor();
    private static final Map<ExecutorKind, LongAdder> CANCELLED = countersFor();
    private static final Map<ExecutorKind, LongAdder> REJECTED = countersFor();

    private AsyncRegistry() {
    }

    private static Map<ExecutorKind, LongAdder> countersFor() {
        Map<ExecutorKind, LongAdder> map = new EnumMap<>(ExecutorKind.class);
        for (ExecutorKind kind : ExecutorKind.values()) {
            map.put(kind, new LongAdder());
        }
        return map;
    }

    static long nextId() {
        return NEXT_ID.getAndIncrement();
    }

    static void register(AsyncTask<?> task) {
        LIVE.put(task.id(), task);
        SUBMITTED.get(task.kind()).increment();
    }

    static void unregister(AsyncTask<?> task, TaskState terminal) {
        LIVE.remove(task.id());
        switch (terminal) {
            case COMPLETED -> COMPLETED.get(task.kind()).increment();
            case FAILED -> FAILED.get(task.kind()).increment();
            case CANCELLED -> CANCELLED.get(task.kind()).increment();
            default -> {
                // PENDING/RUNNING are never the terminal state passed here
            }
        }
        if (AsyncDebug.isEnabled()) {
            recordHistory(task.toInfo());
        }
    }

    static void recordRejected(ExecutorKind kind) {
        REJECTED.get(kind).increment();
    }

    private static void recordHistory(TaskInfo info) {
        HISTORY.addLast(info);
        if (HISTORY_SIZE.incrementAndGet() > HISTORY_LIMIT) {
            HISTORY.pollFirst();
            HISTORY_SIZE.decrementAndGet();
        }
    }

    static AsyncTask<?> find(long id) {
        return LIVE.get(id);
    }

    static TaskInfo findInHistory(long id) {
        for (TaskInfo info : HISTORY) {
            if (info.id() == id) {
                return info;
            }
        }
        return null;
    }

    static List<AsyncTask<?>> liveTasks() {
        return List.copyOf(LIVE.values());
    }

    static int liveCount() {
        return LIVE.size();
    }

    static List<TaskInfo> history() {
        return List.copyOf(HISTORY);
    }

    static int historySize() {
        return HISTORY_SIZE.get();
    }

    static Map<ExecutorKind, LongAdder> submitted() {
        return SUBMITTED;
    }

    static Map<ExecutorKind, LongAdder> completed() {
        return COMPLETED;
    }

    static Map<ExecutorKind, LongAdder> failed() {
        return FAILED;
    }

    static Map<ExecutorKind, LongAdder> cancelled() {
        return CANCELLED;
    }

    static Map<ExecutorKind, LongAdder> rejected() {
        return REJECTED;
    }
}
