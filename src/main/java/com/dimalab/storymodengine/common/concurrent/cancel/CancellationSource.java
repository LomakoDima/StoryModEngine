package com.dimalab.storymodengine.common.concurrent.cancel;

import com.dimalab.storymodengine.api.concurrent.CancellationToken;
import com.dimalab.storymodengine.common.logging.EngineLog;

import java.util.ArrayList;
import java.util.List;

/**
 * The owner half of cancellation — creates and controls a {@link CancellationToken}, the read-only
 * half a task body actually checks. Every {@code AsyncTask} owns one; a {@code .thenX} continuation's
 * source is a {@link #createChild()} of its upstream's, so cancelling upstream cascades down, but
 * cancelling a downstream stage never cancels its upstream — an upstream may be shared by {@code
 * Async.parallel}/{@code Async.race}, which must survive one branch being cancelled.
 *
 * <p>Strictly cooperative: nothing here ever calls {@code Thread.interrupt()}. {@link #cancel()}
 * only flips a flag and fires callbacks/cascades to children — a running task body only actually
 * stops when it next calls {@link CancellationToken#throwIfCancelled()} itself.
 *
 * <p><b>Why a monitor, not the lock-free {@code AtomicBoolean}/{@code CopyOnWriteArrayList} combo
 * used elsewhere in this codebase (e.g. {@code EventBus}'s hot dispatch path)</b>: registering a
 * callback ({@link CancellationToken#onCancel}) and firing them ({@link #cancel()}) race against
 * each other in a way a compare-and-set alone can't close — a callback added the instant after a
 * lock-free {@code cancel()} reads "not yet cancelled" but before it clears the callback list could
 * be silently lost. This is exactly the same class of problem {@code EventBus.structuralLock} already
 * solves for its own (cold, structural) register/unregister path — {@link #cancel()} and {@link
 * CancellationToken#onCancel} are equally cold (cancellation is rare; task creation/registration is
 * not remotely as hot as event dispatch), so the same monitor-guarded correctness trade is the right
 * one here too. {@link #isCancelled()} itself stays a plain {@code volatile} read with no locking —
 * that's the hot, frequently-polled path ({@code throwIfCancelled()} in a tight loop).
 */
public final class CancellationSource {

    private final Object lock = new Object();
    private volatile boolean cancelled;
    private List<Runnable> callbacks;
    private List<CancellationSource> children;
    private final CancellationToken token = new TokenView();

    public static CancellationSource create() {
        return new CancellationSource();
    }

    public CancellationToken token() {
        return token;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    /** Idempotent — fires every registered callback and cascades to every child exactly once, regardless of how many times this is called. */
    public void cancel() {
        List<Runnable> toRun;
        List<CancellationSource> toCascade;
        synchronized (lock) {
            if (cancelled) {
                return;
            }
            cancelled = true;
            toRun = callbacks;
            callbacks = null;
            toCascade = children;
            children = null;
        }
        if (toRun != null) {
            for (Runnable callback : toRun) {
                runSafely(callback);
            }
        }
        if (toCascade != null) {
            for (CancellationSource child : toCascade) {
                child.cancel();
            }
        }
    }

    /** A new source that {@link #cancel()} here cascades into — already-cancelled if {@code this} already is. */
    public CancellationSource createChild() {
        CancellationSource child = new CancellationSource();
        synchronized (lock) {
            if (!cancelled) {
                if (children == null) {
                    children = new ArrayList<>();
                }
                children.add(child);
                return child;
            }
        }
        child.cancel();
        return child;
    }

    /** Links an externally-supplied token (e.g. from {@code Async.run(token, ...)}) as an additional cancellation input — cancelling {@code external} cancels this source too. A no-op for {@link CancellationToken#NONE}. */
    public void linkTo(CancellationToken external) {
        if (external == CancellationToken.NONE) {
            return;
        }
        external.onCancel(this::cancel);
    }

    private static void runSafely(Runnable callback) {
        try {
            callback.run();
        } catch (Exception e) {
            EngineLog.channel("Async").error("Cancellation callback threw", e);
        }
    }

    private final class TokenView implements CancellationToken {

        @Override
        public boolean isCancelled() {
            return CancellationSource.this.isCancelled();
        }

        @Override
        public void onCancel(Runnable callback) {
            synchronized (lock) {
                if (!cancelled) {
                    if (callbacks == null) {
                        callbacks = new ArrayList<>();
                    }
                    callbacks.add(callback);
                    return;
                }
            }
            runSafely(callback);
        }
    }
}
