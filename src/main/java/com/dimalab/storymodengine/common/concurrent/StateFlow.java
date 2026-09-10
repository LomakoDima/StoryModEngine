package com.dimalab.storymodengine.common.concurrent;

import com.dimalab.storymodengine.common.event.Subscription;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * A minimal Java stand-in for Kotlin's {@code StateFlow} — an idea worth borrowing on its own merits,
 * not tied to any HollowEngine source (HE is Kotlin; this project is plain Java, so there is no
 * {@code kotlinx.coroutines} to reuse even if it were architecturally appropriate to). A single
 * current value, readable synchronously at any time via {@link #value()}, that replays itself to a
 * new subscriber immediately on {@link #subscribe} rather than only pushing future changes — the one
 * property {@code common.event.EventBus#subscribe} deliberately doesn't have (a subscriber there only
 * ever sees events posted after it subscribes; there is no notion of "current value" for a plain
 * event stream). {@link #set} is a no-op (no notification) when the new value {@code equals} the
 * current one, matching {@code StateFlow}'s own "distinct until changed" contract.
 *
 * <p>Thread-safe: {@link #subscribe} and {@link #set} are both synchronized on {@code this}, so a
 * subscriber added concurrently with a {@link #set} either sees the old value replayed and then the
 * new one pushed, or is already registered in time to receive the new one directly — never both for
 * the same transition, and never neither. This matters here because the intended producer (an {@code
 * Async.io()} background model load) and the intended consumer (the render thread) are different
 * threads by construction.
 */
public final class StateFlow<T> {

    private final List<Consumer<T>> listeners = new CopyOnWriteArrayList<>();
    private volatile T value;

    public StateFlow(T initial) {
        this.value = Objects.requireNonNull(initial);
    }

    /** The current value, synchronously — no subscription needed for a one-off read. */
    public T value() {
        return value;
    }

    /**
     * Registers {@code listener} and immediately calls it once with the current value, then again on
     * every future {@link #set} that actually changes the value.
     *
     * @return a handle to stop listening — {@link Subscription#unsubscribe()} may be called from
     * within the listener itself (Java's {@code synchronized} is reentrant per-thread), but calling it
     * from another thread while a notification is in flight may still let one more delivery through —
     * an accepted, standard pub-sub tradeoff, not a correctness bug.
     */
    public synchronized Subscription subscribe(Consumer<T> listener) {
        listeners.add(listener);
        listener.accept(value);
        return () -> listeners.remove(listener);
    }

    /** No-op — and no notification — if {@code newValue} already equals the current value. */
    public synchronized void set(T newValue) {
        Objects.requireNonNull(newValue);
        if (newValue.equals(value)) {
            return;
        }
        value = newValue;
        for (Consumer<T> listener : listeners) {
            listener.accept(newValue);
        }
    }
}
