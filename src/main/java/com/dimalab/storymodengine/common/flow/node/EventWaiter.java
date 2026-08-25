package com.dimalab.storymodengine.common.flow.node;

import com.dimalab.storymodengine.api.event.Event;
import com.dimalab.storymodengine.api.event.EventPriority;
import com.dimalab.storymodengine.common.event.Events;
import com.dimalab.storymodengine.common.event.Subscription;
import com.dimalab.storymodengine.common.flow.FlowContext;
import com.dimalab.storymodengine.common.flow.Timeout;
import com.dimalab.storymodengine.common.logging.EngineLog;

import java.util.function.BiPredicate;

/**
 * Waits for one specific event, through the existing {@code EventBus} — not a second event system.
 * {@link #onStart} calls {@code Events.bus().subscribe(eventType, ...)} (the same dynamic-listener
 * extension {@code EventWaiter} was the reason to add) and completes the instant a matching event
 * arrives — genuinely push-based, never polling for the event itself; only an optional {@link
 * Timeout} is ticked, which is bookkeeping, not polling. Built via {@code
 * Flow.waitForEvent(Class, BiPredicate)} / {@code Flow.waitForEvent(Class, BiPredicate, Timeout)}.
 *
 * <p>The filter is a {@code BiPredicate<FlowContext, E>}, not a bare {@code Predicate<E>} — every
 * {@code EventBus} subscription is engine-wide, not per-player, so a multiplayer-safe filter
 * usually needs to check the event against {@code context.player()} (e.g. "is this event about
 * *my* player") — a bare {@code Predicate<E>} would have no way to do that.
 *
 * <p>Always unsubscribes exactly once — on a match, on timeout, and on {@link #onCancel} — so a
 * {@code Parallel} sibling that cancels this waiter (see {@code Parallel}'s Javadoc) never leaks a
 * live subscription. Restoring a flow parked here simply re-subscribes via the same {@link
 * #onStart} the leaf {@code Node#restore} default already calls — correct, not a limitation: a
 * pending subscription has no "progress" to lose the way {@link Wait}'s counter does.
 */
public final class EventWaiter<E extends Event> extends Node {

    private final Class<E> eventType;
    private final BiPredicate<FlowContext, E> filter;
    private final Timeout timeout;

    private Subscription subscription;

    public EventWaiter(Class<E> eventType, BiPredicate<FlowContext, E> filter, Timeout timeout) {
        this.eventType = eventType;
        this.filter = filter;
        this.timeout = timeout;
    }

    @Override
    protected void onStart(FlowContext context) {
        subscription = Events.bus().subscribe(eventType, EventPriority.NORMAL, event -> {
            if (state() != NodeState.RUNNING || !filter.test(context, event)) {
                return;
            }
            EngineLog.channel("Flow").debug("EventWaiter: received {}", eventType.getSimpleName());
            unsubscribe();
            complete();
        });
        EngineLog.channel("Flow").debug("EventWaiter: waiting for {}", eventType.getSimpleName());
    }

    @Override
    protected void onTick(FlowContext context) {
        if (timeout.tick()) {
            EngineLog.channel("Flow").debug("EventWaiter: timed out waiting for {}", eventType.getSimpleName());
            unsubscribe();
            fail();
        }
    }

    @Override
    protected void onCancel(FlowContext context) {
        unsubscribe();
    }

    private void unsubscribe() {
        if (subscription != null) {
            subscription.unsubscribe();
            subscription = null;
        }
    }
}
