package com.dimalab.storymodengine.api.concurrent;

/**
 * What a background task body checks to cooperate with cancellation — the read-only half of {@code
 * common.concurrent.cancel.CancellationSource}, which owns the actual state and creates tokens.
 * Deliberately just this: no {@code cancel()} here (a running body must never cancel itself out from
 * under its own caller), matching the "cooperative, never forced" requirement — nothing in this
 * engine ever calls {@code Thread.interrupt()} on a task body.
 *
 * <pre>{@code
 * Async.run(token, () -> {
 *     while (workRemains()) {
 *         token.throwIfCancelled();
 *         ...
 *     }
 * });
 * }</pre>
 */
public interface CancellationToken {

    /** A token that can never be cancelled — the default when a caller doesn't supply one. */
    CancellationToken NONE = new CancellationToken() {
        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public void onCancel(Runnable callback) {
            // never fires — nothing to register
        }
    };

    boolean isCancelled();

    /** Throws {@link TaskCancelledException} if {@link #isCancelled()} — the one-line check a cooperative loop body calls. */
    default void throwIfCancelled() {
        if (isCancelled()) {
            throw new TaskCancelledException();
        }
    }

    /** Registers a callback to run the moment this token is cancelled — a no-op registration if already cancelled runs it immediately. */
    void onCancel(Runnable callback);
}
