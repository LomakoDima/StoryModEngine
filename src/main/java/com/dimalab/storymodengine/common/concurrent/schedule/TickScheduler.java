package com.dimalab.storymodengine.common.concurrent.schedule;

import com.dimalab.storymodengine.api.concurrent.CancellationToken;
import com.dimalab.storymodengine.api.concurrent.ExecutorKind;
import com.dimalab.storymodengine.common.concurrent.AsyncTask;
import com.dimalab.storymodengine.common.concurrent.cancel.CancellationSource;
import com.dimalab.storymodengine.common.logging.EngineLog;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Minecraft-tick scheduling — deliberately a separate axis from {@code Async}'s wall-clock
 * ({@code Duration}/SCHEDULED-pool) scheduling, never mixed with it (see {@code ARCHITECTURE.md}).
 * Entries are advanced by {@code integration.ConcurrencyTickBridge} at {@code Phase.START}, on the
 * server thread itself — so a due entry's body just runs directly, no {@code MainThreadDispatcher}
 * hand-off needed, and it pauses exactly when the server does.
 */
public final class TickScheduler {

    private static final ConcurrentLinkedQueue<Entry> ENTRIES = new ConcurrentLinkedQueue<>();

    private TickScheduler() {
    }

    /** Runs {@code body} once, after {@code ticks} server ticks. */
    public static AsyncTask<Void> after(int ticks, Runnable body) {
        CancellationSource cancellation = CancellationSource.create();
        CompletableFuture<Void> future = new CompletableFuture<>();
        AsyncTask<Void> handle = AsyncTask.adopt("afterTicks", ExecutorKind.MAIN, cancellation, future);
        Entry entry = new Entry(Math.max(1, ticks), -1, () -> {
            try {
                body.run();
                future.complete(null);
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        }, cancellation.token());
        ENTRIES.add(entry);
        return handle;
    }

    /** Runs {@code body} every {@code periodTicks} server ticks, first firing after one period. The returned task never completes on its own — {@link AsyncTask#cancel()} is the only way it ends; a firing that throws is logged and does not stop later firings. */
    public static AsyncTask<Void> every(int periodTicks, Runnable body) {
        int period = Math.max(1, periodTicks);
        CancellationSource cancellation = CancellationSource.create();
        CompletableFuture<Void> future = new CompletableFuture<>();
        AsyncTask<Void> handle = AsyncTask.adopt("everyTick", ExecutorKind.MAIN, cancellation, future);
        Entry entry = new Entry(period, period, () -> {
            try {
                body.run();
            } catch (Throwable t) {
                EngineLog.channel("Async").error("Recurring tick task threw", t);
            }
        }, cancellation.token());
        ENTRIES.add(entry);
        return handle;
    }

    /** Called only by {@code integration.ConcurrencyTickBridge}, on the server thread, once per tick. */
    public static void tick() {
        for (Entry entry : ENTRIES) {
            if (entry.token.isCancelled()) {
                ENTRIES.remove(entry);
                continue;
            }
            if (entry.remaining.decrementAndGet() <= 0) {
                if (entry.period < 0) {
                    ENTRIES.remove(entry);
                } else {
                    entry.remaining.set(entry.period);
                }
                entry.body.run();
            }
        }
    }

    public static int size() {
        return ENTRIES.size();
    }

    /** Called only by {@code integration.AsyncLifecycle} on {@code ServerStoppingEvent}. */
    public static void clear() {
        ENTRIES.clear();
    }

    private static final class Entry {
        final AtomicInteger remaining;
        final int period;
        final Runnable body;
        final CancellationToken token;

        Entry(int initial, int period, Runnable body, CancellationToken token) {
            this.remaining = new AtomicInteger(initial);
            this.period = period;
            this.body = body;
            this.token = token;
        }
    }
}
