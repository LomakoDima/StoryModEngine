package com.dimalab.storymodengine.common.entity.search;

import com.dimalab.storymodengine.common.flow.FlowContext;
import com.dimalab.storymodengine.common.flow.Result;
import com.dimalab.storymodengine.common.flow.Task;
import com.dimalab.storymodengine.common.scripting.ast.SmeValueType;
import com.dimalab.storymodengine.common.scripting.persistence.StoryVariableStore;
import net.minecraft.world.entity.Entity;

import java.util.UUID;

/**
 * {@code await_entity <targetVar> <timeoutTicks>} — SME's answer to HollowEngine's {@code
 * Ref<T>.resolve()}, minus the "wait forever" foot-gun (this session's own confirmed scope decision):
 * a plain per-tick poll of {@code level.getEntity(uuid)} rather than HE's {@code EntityLoadedEvent}
 * machinery — SME's {@link Task} already ticks every frame, so an event-driven suspend mechanism
 * would add real complexity for no behavioral difference. Polls {@code targetVar} itself (not a
 * captured UUID) so it also picks up a value written *after* this task started, matching how every
 * other Blackboard/variable read in this engine works.
 */
public final class AwaitEntityTask implements Task {

    private final String targetVar;
    private final int timeoutTicks;
    private int elapsed = 0;

    public AwaitEntityTask(String targetVar, int timeoutTicks) {
        this.targetVar = targetVar;
        this.timeoutTicks = timeoutTicks;
    }

    @Override
    public Result tick(FlowContext context) {
        String raw;
        try {
            raw = StoryVariableStore.get(context.player(), targetVar, SmeValueType.STRING).stringVal();
        } catch (NullPointerException e) {
            // Same "capabilities invalidated exactly at disconnect" window PersistentWaitTask guards
            // against — StoryVariableStore.varsFor has no null-guard of its own for this case, so
            // this task takes on the responsibility rather than crash the flow (logged as "Task
            // threw, treating as failure") over a transient, tick-scoped condition.
            return Result.RUNNING;
        }
        if (raw == null || raw.isEmpty()) {
            return Result.FAILURE;
        }
        UUID id;
        try {
            id = UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return Result.FAILURE;
        }
        Entity entity = context.player().serverLevel().getEntity(id);
        if (entity != null && entity.isAlive()) {
            return Result.SUCCESS;
        }
        elapsed++;
        return elapsed >= timeoutTicks ? Result.FAILURE : Result.RUNNING;
    }
}
