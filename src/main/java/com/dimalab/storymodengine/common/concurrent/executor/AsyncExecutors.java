package com.dimalab.storymodengine.common.concurrent.executor;

import com.dimalab.storymodengine.api.concurrent.ExecutorKind;
import com.dimalab.storymodengine.common.logging.EngineLog;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Owns the three real thread pools (CPU/IO/SCHEDULED) plus a {@link MainThreadDispatcher}, for one
 * "session" — one Minecraft server lifetime. Instantiable rather than purely static, for two
 * reasons: (1) {@code integration.AsyncLifecycle} creates a fresh instance on every {@code
 * ServerStartingEvent} and installs it as {@link #current()}, so an integrated-server world
 * unload/reload gets genuinely live pools rather than reused shut-down ones; (2) a self-test can
 * build a throwaway instance via {@link #createForTest} and exercise the real start/shutdown
 * sequence against it without ever touching the pools other code is actively using.
 */
public final class AsyncExecutors {

    public enum State { NOT_STARTED, RUNNING, SHUTTING_DOWN, TERMINATED }

    private static volatile AsyncExecutors current = new AsyncExecutors();

    private volatile State state = State.NOT_STARTED;
    private volatile ExecutorService cpu;
    private volatile ExecutorService io;
    private volatile ScheduledExecutorService scheduled;
    private final MainThreadDispatcher main = new MainThreadDispatcher();

    /** The live session's pools — what {@code Async}'s own facade methods read. Before the first {@code ServerStartingEvent}, this is a fresh, {@code NOT_STARTED} instance whose {@link #isAccepting()} is {@code false}. */
    public static AsyncExecutors current() {
        return current;
    }

    /** Called only by {@code integration.AsyncLifecycle} on {@code ServerStartingEvent}. */
    public static void installAsCurrent(AsyncExecutors executors) {
        current = executors;
    }

    /** Builds and starts a throwaway instance for a self-test to exercise real start/shutdown against — never installed as {@link #current()}. */
    public static AsyncExecutors createForTest(ExecutorFactory factory) {
        AsyncExecutors executors = new AsyncExecutors();
        executors.start(factory);
        return executors;
    }

    public void start(ExecutorFactory factory) {
        this.cpu = factory.createCpu();
        this.io = factory.createIo();
        this.scheduled = factory.createScheduled();
        this.state = State.RUNNING;
    }

    public State state() {
        return state;
    }

    public boolean isAccepting() {
        return state == State.RUNNING;
    }

    /** Returns whatever {@link ExecutorFactory} handed back for {@code CPU}/{@code IO} — a {@code ThreadPoolExecutor} today, not necessarily one once a Virtual Thread backend exists (see {@link ExecutorFactory}). {@link com.dimalab.storymodengine.common.concurrent.AsyncDebug} downcasts defensively for pool gauges; nothing else should assume the concrete type. */
    public ExecutorService executorFor(ExecutorKind kind) {
        return switch (kind) {
            case CPU -> cpu;
            case IO -> io;
            case SCHEDULED -> throw new IllegalArgumentException("SCHEDULED has no ThreadPoolExecutor — use scheduled() instead");
            case MAIN -> throw new IllegalArgumentException("MAIN has no ThreadPoolExecutor — use main() instead");
        };
    }

    public ScheduledExecutorService scheduled() {
        return scheduled;
    }

    public MainThreadDispatcher main() {
        return main;
    }

    /** The full shutdown sequence — see {@code integration.AsyncLifecycle} for when this is called and why each step exists. Idempotent. */
    public void shutdown() {
        if (state == State.TERMINATED) {
            return;
        }
        state = State.SHUTTING_DOWN;

        // SCHEDULED never holds user work by design (it only ever hands bodies off to CPU/IO/MAIN),
        // so discarding whatever's queued there outright is correct and instant.
        scheduled.shutdownNow();

        cpu.shutdown();
        io.shutdown();
        boolean cpuDone = awaitQuiet(cpu, 5);
        boolean ioDone = awaitQuiet(io, 5);
        if (!cpuDone) {
            cpu.shutdownNow();
            cpuDone = awaitQuiet(cpu, 1);
        }
        if (!ioDone) {
            io.shutdownNow();
            ioDone = awaitQuiet(io, 1);
        }

        state = State.TERMINATED;
        if (cpuDone && ioDone) {
            EngineLog.channel("Async").info("Executors stopped cleanly.");
        } else {
            EngineLog.channel("Async").warn(
                    "Executors did not fully terminate within the shutdown window — cpu={} io={} — possible thread leak", cpuDone, ioDone);
        }
    }

    private static boolean awaitQuiet(java.util.concurrent.ExecutorService service, int seconds) {
        try {
            return service.awaitTermination(seconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
