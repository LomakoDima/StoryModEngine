package com.dimalab.storymodengine.common.trigger.example;

import com.dimalab.storymodengine.api.trigger.annotation.AutoTrigger;
import com.dimalab.storymodengine.common.event.bridge.PlayerConnectedEvent;
import com.dimalab.storymodengine.common.flow.Flow;
import com.dimalab.storymodengine.common.logging.EngineLog;
import com.dimalab.storymodengine.common.trigger.Trigger;
import net.minecraft.core.BlockPos;

/**
 * The required demonstration triggers, one per {@code TriggerKind} — {@code @AutoTrigger} registers
 * each automatically, no manual {@code TriggerRegistry.register} call anywhere (proof by
 * construction, the same way {@code QuestExamples}/{@code DialogueExamples} prove their own
 * {@code @Auto*} annotations). Each just logs to demonstrate the trigger actually fired and a real
 * {@code Flow} instance actually ran — a mod author's own trigger would {@code .run(...)} something
 * that starts a Dialogue/Cutscene/Quest instead, exactly like {@code Objective.dialogue(...)} does
 * for Quest.
 */
public final class TriggerExamples {

    /** Walk within 5 blocks of {@code (100, 64, 200)} — fires once per player. */
    @AutoTrigger
    public static final Trigger SPAWN_AREA_ENTRY = Trigger.location("spawn_area_entry")
            .radius(new BlockPos(100, 64, 200), 5)
            .whenEntered()
            .once()
            .run(Flow.action(ctx -> EngineLog.channel("Trigger").success(
                    "{} entered the spawn area trigger zone", ctx.player().getGameProfile().getName()).toChat(ctx.player())));

    /** Fires once server-wide, for whoever happens to be online, the first time day-time crosses into night (13000). */
    @AutoTrigger
    public static final Trigger NIGHTFALL = Trigger.time("nightfall")
            .at(13000)
            .once()
            .run(Flow.action(ctx -> EngineLog.channel("Trigger").info("Night has fallen.").toChat(ctx.player())));

    /** Fires every time a player connects — {@code PlayerConnectedEvent} already exposes {@code player()}, so no explicit extractor is needed (see {@code Trigger.Builder#on(Class)}). */
    @AutoTrigger
    public static final Trigger WELCOME_BACK = Trigger.event("welcome_back")
            .on(PlayerConnectedEvent.class)
            .repeat()
            .run(Flow.action(ctx -> EngineLog.channel("Trigger").info(
                    "Welcome back, {}!", ctx.player().getGameProfile().getName()).toChat(ctx.player())));

    private TriggerExamples() {
    }
}
