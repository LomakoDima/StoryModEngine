package com.dimalab.storymodengine.common.flow.example;

import com.dimalab.storymodengine.common.flow.Flow;
import com.dimalab.storymodengine.common.flow.FlowDefinition;
import com.dimalab.storymodengine.common.flow.Scope;
import com.dimalab.storymodengine.common.flow.Transition;
import com.dimalab.storymodengine.api.flow.annotation.AutoFlow;
import com.dimalab.storymodengine.common.logging.EngineLog;
import net.minecraft.resources.ResourceLocation;

/**
 * The required demonstration of the expanded runtime — {@code Choice} → {@code Wait} → {@code
 * SubFlow} → {@code EventWaiter} in one Flow, run via {@code /storymodengine flowdemo}.
 *
 * <pre>
 * Sequence
 *  ├─ Action:    "Flow started"
 *  ├─ Choice("optionA" → set var "path"=A → Wait 60 ticks,
 *             "optionB" → set var "path"=B → Wait 60 ticks)
 *  ├─ Action:    report which path was taken (Blackboard read-back)
 *  ├─ SubFlow:   a short nested Sequence (proves composition, not a second runtime)
 *  ├─ Action:    "SubFlow completed"
 *  ├─ Action:    "Waiting for event..."
 *  ├─ EventWaiter(FlowDemoSignalEvent, scoped to this player)
 *  └─ Action:    "Event received"
 * </pre>
 */
public final class FlowDemoScenario {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("storymodengine", "flow_demo");

    /** {@code @AutoFlow}-registered — proves the discovery mechanism, not manually registered anywhere. */
    @AutoFlow
    public static final FlowDefinition DEMO = new FlowDefinition(ID, Flow.sequence(
            Flow.action(ctx -> EngineLog.channel("Flow").success("Flow started").toChat(ctx.player())),
            Flow.choice(
                    new Transition("optionA", path("A")),
                    new Transition("optionB", path("B"))),
            Flow.action(ctx -> EngineLog.channel("Flow").info(
                    "Path taken: {}", ctx.<String>getVariable(Scope.INSTANCE, "path")).toChat(ctx.player())),
            Flow.subFlow(Flow.sequence(
                    Flow.action(ctx -> ctx.log("SubFlow: inner step running")),
                    Flow.action(ctx -> ctx.setVariable(Scope.INSTANCE, "subFlowRan", true)))),
            Flow.action(ctx -> EngineLog.channel("Flow").success("SubFlow completed").toChat(ctx.player())),
            Flow.action(ctx -> EngineLog.channel("Flow").info("Waiting for event...").toChat(ctx.player())),
            Flow.waitForEvent(FlowDemoSignalEvent.class,
                    (ctx, event) -> event.player() != null && event.player().equals(ctx.player())),
            Flow.action(ctx -> EngineLog.channel("Flow").success("Event received").toChat(ctx.player()))
    ));

    private FlowDemoScenario() {
    }

    private static Flow path(String label) {
        return Flow.sequence(
                Flow.action(ctx -> {
                    ctx.setVariable(Scope.INSTANCE, "path", label);
                    EngineLog.channel("Flow").success("Choice selected: {}", label).toChat(ctx.player());
                }),
                Flow.wait(60));
    }
}
