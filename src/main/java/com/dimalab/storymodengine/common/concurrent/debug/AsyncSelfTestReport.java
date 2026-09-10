package com.dimalab.storymodengine.common.concurrent.debug;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A thread-safe pass/fail collector for {@code /sme async selftest} — checks land here
 * from whichever executor they happened to run on (that's the entire point of the suite), so every
 * mutation is lock-free/atomic rather than assuming the caller's thread. {@link #onDone} fires
 * exactly once, whichever comes first: every registered check calling {@link #finishOne()}, or the
 * deadline calling {@link #forceDone()} — see {@code AsyncSelfTestCommand} for why a synchronous
 * command can't simply block until the suite finishes.
 */
public final class AsyncSelfTestReport {

    private final Queue<String> failures = new ConcurrentLinkedQueue<>();
    private final AtomicInteger passed = new AtomicInteger();
    private final AtomicInteger remaining;
    private final Runnable onDone;
    private final AtomicBoolean doneFired = new AtomicBoolean(false);

    public AsyncSelfTestReport(int totalChecks, Runnable onDone) {
        this.remaining = new AtomicInteger(totalChecks);
        this.onDone = onDone;
    }

    public void checkTrue(String name, boolean condition) {
        if (condition) {
            passed.incrementAndGet();
        } else {
            failures.add(name);
        }
    }

    public void fail(String name, String reason) {
        failures.add(name + " — " + reason);
    }

    /** Call exactly once per registered check, once that check has recorded its outcome. */
    public void finishOne() {
        if (remaining.decrementAndGet() <= 0) {
            fireDone();
        }
    }

    /** Called by the suite's own deadline timer — whatever hasn't reported by then is implicitly incomplete, not counted as passed. */
    public void forceDone() {
        fireDone();
    }

    private void fireDone() {
        if (doneFired.compareAndSet(false, true)) {
            onDone.run();
        }
    }

    public int passedCount() {
        return passed.get();
    }

    public List<String> failures() {
        return List.copyOf(failures);
    }
}
