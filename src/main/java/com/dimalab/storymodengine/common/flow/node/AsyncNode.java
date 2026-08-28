package com.dimalab.storymodengine.common.flow.node;

import com.dimalab.storymodengine.api.concurrent.AsyncResult;
import com.dimalab.storymodengine.common.concurrent.AsyncTask;
import com.dimalab.storymodengine.common.concurrent.integration.FlowResumeQueue;
import com.dimalab.storymodengine.common.flow.FlowContext;
import com.dimalab.storymodengine.common.logging.EngineLog;

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Suspends the flow on a background {@link AsyncTask} — built via {@code Flow.async(Function)}/
 * {@code Flow.await(Function, BiConsumer)}. Mirrors {@link EventWaiter}'s subscribe-in-{@code
 * onStart}/release-in-{@code onCancel} shape, but never calls {@link #complete()}/{@link #fail()}
 * from the background thread the task actually completes on — {@code Node#state} is a plain,
 * non-volatile field, unsafe to write from anywhere but the thread driving the flow. Instead the
 * completion is hopped onto {@link FlowResumeQueue}, drained on the server thread at {@code
 * Phase.START} — strictly before the tick that would otherwise observe stale state — by {@code
 * concurrent.integration.ConcurrencyTickBridge}. No {@link #onTick} override and no {@code Timeout}
 * field: a per-task deadline belongs to the {@link AsyncTask} itself ({@code AsyncTask#timeout}), not
 * this node — see {@code Timeout}'s own Javadoc for why a shared mutable {@code Timeout} instance
 * would be unsafe to capture in a {@code Flow} definition reused across players.
 *
 * <p>Restoring a flow parked here re-{@link #onStart}s — the leaf default — meaning the background
 * operation relaunches from scratch after a restart. Documented, accepted limitation, the same shape
 * {@code Action}/{@code Wait} already carry for a node that was mid-flight at save time.
 *
 * <p><b>Never register a bare {@code Flow.async(...)}/{@code Flow.await(...)} as a top-level Flow —
 * always wrap it in {@code Flow.sequence(...)} (even a sequence of one), or nest it inside one.</b>
 * {@code FlowManager.tick()} re-reads a Flow's root state fresh at the start of every tick rather
 * than carrying the previous tick's observed state forward (a real, pre-existing, separately-scoped
 * limitation — not something this package changes). A composite root notices its child's completion
 * correctly (every composite's {@code onTick} unconditionally re-reads {@code current.state()}, so
 * this drains-at-{@code Phase.START}-then-ticks-at-{@code Phase.END} ordering resolves the child
 * *before* the composite's own {@code onTick} runs, within {@code FlowManager.tick()}'s own before/
 * after comparison for that tick). A bare-root {@code AsyncNode} completing off-stack has no such
 * composite watching it — the transition is invisible to that same before/after comparison and the
 * flow is stranded, never reported complete. In practice this costs nothing: a real Flow.await step
 * is virtually always one step among several anyway.
 */
public final class AsyncNode<T> extends Node {

    private final Function<FlowContext, AsyncTask<T>> starter;
    private final BiConsumer<FlowContext, T> consumer;

    private AsyncTask<T> task;

    /** {@code consumer} is {@code null} for {@code Flow.async} (fire-and-await, result discarded); non-null for {@code Flow.await}. */
    public AsyncNode(Function<FlowContext, AsyncTask<T>> starter, BiConsumer<FlowContext, T> consumer) {
        this.starter = starter;
        this.consumer = consumer;
    }

    @Override
    protected void onStart(FlowContext context) {
        task = starter.apply(context);
        task.onComplete(result -> FlowResumeQueue.offer(() -> onSettled(context, result)));
    }

    private void onSettled(FlowContext context, AsyncResult<T> result) {
        if (state() != NodeState.RUNNING) {
            // Already cancelled (or, in principle, moved on) between the background completion and
            // this drain — onCancel already released the task; nothing left to do.
            return;
        }
        if (result.isSuccess()) {
            if (consumer != null) {
                consumer.accept(context, result.value().orElse(null));
            }
            complete();
        } else {
            if (result.isFailure()) {
                EngineLog.channel("Flow").warn("AsyncNode: task failed — {}",
                        result.error().map(Throwable::getMessage).orElse("unknown"));
            }
            fail();
        }
    }

    @Override
    protected void onCancel(FlowContext context) {
        if (task != null) {
            task.cancel();
        }
    }
}
