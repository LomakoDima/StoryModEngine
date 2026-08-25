package com.dimalab.storymodengine.common.flow;

import com.dimalab.storymodengine.api.flow.Evaluator;
import com.dimalab.storymodengine.api.event.Event;
import com.dimalab.storymodengine.common.flow.node.Action;
import com.dimalab.storymodengine.common.flow.node.Branch;
import com.dimalab.storymodengine.common.flow.node.Checkpoint;
import com.dimalab.storymodengine.common.flow.node.Choice;
import com.dimalab.storymodengine.common.flow.node.Condition;
import com.dimalab.storymodengine.common.flow.node.EventWaiter;
import com.dimalab.storymodengine.common.flow.node.Node;
import com.dimalab.storymodengine.common.flow.node.Parallel;
import com.dimalab.storymodengine.common.flow.node.PolicyNode;
import com.dimalab.storymodengine.common.flow.node.Selector;
import com.dimalab.storymodengine.common.flow.node.Sequence;
import com.dimalab.storymodengine.common.flow.node.SubFlow;
import com.dimalab.storymodengine.common.flow.node.TaskAction;
import com.dimalab.storymodengine.common.flow.node.Wait;

import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A Flow *definition* — an immutable recipe for building a fresh {@code Node} tree, never a shared
 * tree of live, stateful nodes. This is the direct answer to "Flow Definition ≠ Flow Runtime": the
 * same {@code Flow story = Flow.sequence(...)} value can back any number of independent, concurrent
 * {@code FlowRuntime}s (one player, another player, ...) because {@link #instantiate()} builds a
 * brand-new {@code Node} graph every time it's called — nothing about a definition's own mutable
 * state could ever leak between runtimes, because there is no mutable state on a definition at all.
 * {@link FlowDefinition} pairs a {@code Flow} with a stable id for registration/persistence; a bare
 * {@code Flow} (this class) is the composable currency used everywhere else, including as a
 * {@code FlowDefinition}'s own graph.
 *
 * <pre>{@code
 * Flow story = Flow.sequence(
 *     Flow.action(ctx -> ctx.log("NPC appears")),
 *     Flow.action(ctx -> ctx.log("Give quest")),
 *     Flow.condition(ctx -> Capabilities.get(ctx.player(), StoryPlayerData.class).storyPoints >= 1),
 *     Flow.choice(
 *         new Transition("accept", Flow.action(ctx -> { ... })),
 *         new Transition("decline", Flow.action(ctx -> { ... }))
 *     )
 * );
 * }</pre>
 *
 * A mod author never constructs {@code Action}/{@code Condition}/{@code Sequence}/{@code
 * Selector}/{@code Parallel}/{@code Branch}/{@code Choice}/{@code Wait}/{@code SubFlow}/{@code
 * EventWaiter}/{@code TaskAction}/{@code PolicyNode}/{@code Checkpoint} directly — these static
 * factories are the whole surface; the {@code node} package's classes exist to be instantiated by
 * them, not named at a call site.
 */
public final class Flow {

    private final Supplier<Node> factory;

    private Flow(Supplier<Node> factory) {
        this.factory = factory;
    }

    /** Builds a fresh, independent {@code Node} tree — call once per {@code FlowRuntime}, never shared between runtimes. */
    public Node instantiate() {
        return factory.get();
    }

    /** An action that always succeeds unless it throws (caught and treated as a failure — see {@code Action}). */
    public static Flow action(Consumer<FlowContext> action) {
        return new Flow(() -> new Action(action));
    }

    /**
     * An action that explicitly reports success/failure via its return value. A separate name from
     * {@link #action(Consumer)} rather than a same-named overload — Java cannot disambiguate two
     * single-abstract-method overloads by a plain expression-statement lambda body alone, so an
     * overload here would force every call site to pick one via an explicit cast.
     */
    public static Flow actionResult(Function<FlowContext, Boolean> action) {
        return new Flow(() -> new Action(action));
    }

    /** Completes if {@code evaluator} returns true, fails otherwise — see {@code Condition}. */
    public static Flow condition(Evaluator<Boolean> evaluator) {
        return new Flow(() -> new Condition(evaluator));
    }

    /** Runs {@code children} one after another; a failed child fails the whole sequence without starting the rest — see {@code Sequence}. */
    public static Flow sequence(Flow... children) {
        List<Flow> copy = List.of(children);
        return new Flow(() -> new Sequence(copy));
    }

    /** Runs {@code children} concurrently; completes when all complete, fails (cancelling the rest) as soon as any one fails — see {@code Parallel}. */
    public static Flow parallel(Flow... children) {
        List<Flow> copy = List.of(children);
        return new Flow(() -> new Parallel(copy));
    }

    /**
     * Runs {@code children} one after another; stops and completes at the first child that
     * succeeds. Fails only if every child fails — the standard behavior-tree "Selector"/"Fallback",
     * the OR to {@link #sequence}'s AND — see {@code Selector}.
     */
    public static Flow selector(Flow... children) {
        List<Flow> copy = List.of(children);
        return new Flow(() -> new Selector(copy));
    }

    /** Picks {@code whenTrue}/{@code whenFalse} once, based on {@code condition} — see {@code Branch}. */
    public static Flow branch(Evaluator<Boolean> condition, Flow whenTrue, Flow whenFalse) {
        return new Flow(() -> new Branch(condition, whenTrue, whenFalse));
    }

    /** Waits for one of {@code options} to be externally selected — see {@code Choice}. */
    public static Flow choice(Transition... options) {
        List<Transition> copy = List.of(options);
        return new Flow(() -> new Choice(copy));
    }

    /** Completes after {@code ticks} engine ticks — see {@code Wait}. */
    public static Flow wait(int ticks) {
        return new Flow(() -> new Wait(ticks));
    }

    /** Waits for the next {@code eventType} matching {@code filter}, with no timeout — see {@code EventWaiter}. */
    public static <E extends Event> Flow waitForEvent(Class<E> eventType, BiPredicate<FlowContext, E> filter) {
        return waitForEvent(eventType, filter, Timeout.NONE);
    }

    /** Same as {@link #waitForEvent(Class, BiPredicate)}, but fails if no matching event arrives within {@code timeout}. */
    public static <E extends Event> Flow waitForEvent(Class<E> eventType, BiPredicate<FlowContext, E> filter, Timeout timeout) {
        return new Flow(() -> new EventWaiter<>(eventType, filter, timeout));
    }

    /** Runs {@code inner} as a single composed step of this Flow — see {@code SubFlow}. */
    public static Flow subFlow(Flow inner) {
        return new Flow(() -> new SubFlow(inner));
    }

    /** Runs a fresh {@link Task} (from {@code factory}) every tick until it reports non-{@code RUNNING} — see {@code TaskAction}. */
    public static Flow task(Supplier<Task> factory) {
        return new Flow(() -> new TaskAction(factory));
    }

    /** A no-op, self-documenting "safe to resume from here" marker — see {@code Checkpoint}. */
    public static Flow checkpoint() {
        return new Flow(Checkpoint::new);
    }

    /** Reinterprets {@code child}'s failure according to {@code policy} — see {@code PolicyNode}/{@link FailurePolicy}. */
    public static Flow withPolicy(FailurePolicy policy, Flow child) {
        return new Flow(() -> new PolicyNode(policy, child, null, 1));
    }

    /**
     * Defers resolving which {@code Flow} to run until this branch is actually instantiated, rather
     * than requiring an already-built {@code Flow} value up front the way every other factory here
     * does. Every other factory captures its children eagerly ({@code sequence}/{@code branch}/
     * {@code choice}/{@code subFlow} all take {@code Flow} values the caller already constructed),
     * which is fine for a tree but cannot express a forward reference to a {@code Flow} that doesn't
     * exist yet or a cyclic one that refers back to itself — building such a graph by calling static
     * factories directly would recurse infinitely in the *caller*, before any {@code Node} is ever
     * instantiated. {@code lazy} breaks that by only calling {@code supplier} once this branch is
     * actually reached and turned into a {@code Node} — a caller can close over a mutable lookup
     * (e.g. a {@code Map<String, Flow>} being filled in as a larger graph compiles) as long as it's
     * fully populated by the time anything actually instantiates. No new {@code Node} subtype: the
     * resulting node is whatever {@code supplier.get()} would have produced directly.
     */
    public static Flow lazy(Supplier<Flow> supplier) {
        return new Flow(() -> supplier.get().instantiate());
    }

    /** Re-runs {@code child} up to {@code maxAttempts} times before propagating failure — see {@code PolicyNode}. */
    public static Flow retry(int maxAttempts, Flow child) {
        return new Flow(() -> new PolicyNode(FailurePolicy.RETRY, child, null, maxAttempts));
    }

    /** Runs {@code alternative} if {@code primary} fails — see {@code PolicyNode}. */
    public static Flow fallback(Flow primary, Flow alternative) {
        return new Flow(() -> new PolicyNode(FailurePolicy.FALLBACK, primary, alternative, 1));
    }
}
