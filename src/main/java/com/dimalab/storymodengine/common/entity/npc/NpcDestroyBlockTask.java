package com.dimalab.storymodengine.common.entity.npc;

import com.dimalab.storymodengine.common.flow.FlowContext;
import com.dimalab.storymodengine.common.flow.Result;
import com.dimalab.storymodengine.common.flow.Scope;
import com.dimalab.storymodengine.common.flow.Task;

/**
 * Backs {@code npc_break_block} when it's compiled from a suspendable {@code sequence}/{@code trigger}
 * body — same shape as {@link NpcMoveToTask}, which this is a direct copy of: polls once per tick until
 * {@link NpcEntity#destroyBlockTarget()} goes back to {@code null} (cleared by {@code
 * NpcDestroyBlockGoal} on real success or a pre-flight abort — see its own doc for which is which),
 * resolving {@link Result#SUCCESS} then. Reads {@link NpcMoveToTask#GENERATION_KEY} off the same {@code
 * Scope#INSTANCE} blackboard slot {@code npc_move_to} uses — both share one "movement channel," so
 * either one being superseded by the other (or by {@code npc_stop_move}) means this wait should end the
 * same way {@code NpcMoveToTask} already does, not just the same-named command repeating.
 */
public final class NpcDestroyBlockTask implements Task {

    private final String npcName;

    public NpcDestroyBlockTask(String npcName) {
        this.npcName = npcName;
    }

    @Override
    public Result tick(FlowContext context) {
        NpcEntity npc = NpcLookup.resolve(context.player().serverLevel(), npcName);
        if (npc == null) {
            return Result.FAILURE;
        }
        Long expectedGeneration = context.getVariable(Scope.INSTANCE, NpcMoveToTask.GENERATION_KEY);
        if (expectedGeneration == null || npc.movementGeneration() != expectedGeneration) {
            return Result.CANCELLED;
        }
        return npc.destroyBlockTarget() == null ? Result.SUCCESS : Result.RUNNING;
    }

    @Override
    public void cancel(FlowContext context) {
        NpcEntity npc = NpcLookup.resolve(context.player().serverLevel(), npcName);
        if (npc != null) {
            npc.setDestroyBlockTarget(null);
            npc.getNavigation().stop();
        }
    }
}
