package com.dimalab.storymodengine.common.concurrent.integration;

import com.dimalab.storymodengine.common.logging.EngineLog;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * The one multi-producer/single-consumer handoff this whole design needs — a background thread that
 * just finished an {@code AsyncTask} a {@code flow.node.AsyncNode} is waiting on offers its
 * completion here instead of ever touching {@code Node} state itself (which is a plain,
 * non-volatile field — see {@code ConcurrencyBootstrap}'s Javadoc for why). {@link
 * ConcurrencyTickBridge} drains this at {@code Phase.START}, on the server thread,
 * strictly before {@code flow.integration.FlowTickBridge}'s {@code Phase.END} handler runs — so the
 * state change is always observed within the same tick it happened.
 */
public final class FlowResumeQueue {

    private static final ConcurrentLinkedQueue<Runnable> QUEUE = new ConcurrentLinkedQueue<>();

    private FlowResumeQueue() {
    }

    /** Called from any thread — typically an {@code AsyncTask} completion callback. */
    public static void offer(Runnable resume) {
        QUEUE.offer(resume);
    }

    /** Called only by {@link ConcurrencyTickBridge}, on the server thread. */
    static void drain() {
        Runnable resume;
        while ((resume = QUEUE.poll()) != null) {
            try {
                resume.run();
            } catch (Exception e) {
                EngineLog.channel("Async").error("Flow resume threw", e);
            }
        }
    }

    /** Called only by {@code AsyncLifecycle} on {@code ServerStoppingEvent}. */
    public static void clear() {
        QUEUE.clear();
    }

    public static int size() {
        return QUEUE.size();
    }
}
