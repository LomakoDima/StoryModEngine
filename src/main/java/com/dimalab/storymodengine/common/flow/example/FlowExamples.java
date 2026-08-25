package com.dimalab.storymodengine.common.flow.example;

import com.dimalab.storymodengine.common.capabilities.Capabilities;
import com.dimalab.storymodengine.common.capabilities.example.ModCapabilities;
import com.dimalab.storymodengine.common.capabilities.example.StoryPlayerData;
import com.dimalab.storymodengine.common.flow.Flow;
import com.dimalab.storymodengine.common.flow.FlowContext;
import com.dimalab.storymodengine.common.flow.Transition;
import com.dimalab.storymodengine.common.logging.EngineLog;
import net.minecraft.resources.ResourceLocation;

/**
 * The task's own required example scenario, built entirely from {@link Flow}'s public factories —
 * Flow itself never learns anything about "Story Points" here; that's just what this particular
 * {@code Action}/{@code Condition} choose to do with the existing {@code Capabilities} facade.
 *
 * <pre>
 * Sequence
 *  ├─ Action:    "Story Flow started"
 *  ├─ Action:    +1 Story Point
 *  ├─ Condition: Story Points >= 1?  (fails the sequence outright if not)
 *  └─ Choice("accept" → +5 points, "decline" → -1 point)
 * </pre>
 */
public final class FlowExamples {

    public static final ResourceLocation STORY_TEST_ID = ResourceLocation.fromNamespaceAndPath("storymodengine", "flow_test");

    public static final Flow STORY_TEST = Flow.sequence(
            Flow.action(ctx -> EngineLog.channel("Flow").success("Story Flow started").toChat(ctx.player())),
            Flow.action(ctx -> addStoryPoints(ctx, 1)),
            Flow.condition(ctx -> storyPoints(ctx) >= 1),
            Flow.choice(
                    new Transition("accept", Flow.action(ctx -> addStoryPoints(ctx, 5))),
                    new Transition("decline", Flow.action(ctx -> addStoryPoints(ctx, -1)))
            )
    );

    private FlowExamples() {
    }

    private static int storyPoints(FlowContext ctx) {
        return Capabilities.get(ctx.player(), ModCapabilities.STORY_DATA).storyPoints;
    }

    private static void addStoryPoints(FlowContext ctx, int amount) {
        StoryPlayerData data = Capabilities.get(ctx.player(), ModCapabilities.STORY_DATA);
        data.storyPoints += amount;
        Capabilities.markDirty(ctx.player(), ModCapabilities.STORY_DATA);
        ModCapabilities.STORY_DATA.sync(ctx.player());
        EngineLog.channel("Flow").success("Story Points: {}", data.storyPoints).toChat(ctx.player());
    }
}
