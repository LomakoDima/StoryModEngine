package com.dimalab.storymodengine.common.entity.npc;

import com.dimalab.storymodengine.common.flow.FlowContext;
import com.dimalab.storymodengine.common.flow.Result;
import com.dimalab.storymodengine.common.flow.Scope;
import com.dimalab.storymodengine.common.flow.Task;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.phys.Vec3;

/**
 * Backs {@code npc_move_to} when it's compiled from a suspendable {@code sequence}/{@code trigger}
 * body (see {@code SequenceCompiler#compileAwaitableMoveTo}) — the first real workload for {@link
 * Task}/{@link com.dimalab.storymodengine.common.flow.node.TaskAction}, whose own doc names
 * "movement" as exactly what this seam was left for.
 *
 * <p>Polls vanilla's own {@link PathNavigation} once per tick until it stops pathing (verified
 * against the decompiled source: {@code isDone()} is {@code path == null || path.isDone()}, and
 * vanilla's own {@code doStuckDetection}/{@code timeoutPath} already call {@code stop()} on getting
 * stuck or timing out — nothing here needs to reimplement stuck detection), then tells "arrived" from
 * "gave up" by proximity to the original target.
 *
 * <p>Reads {@link #GENERATION_KEY} off the flow instance's own {@code Blackboard} every tick — written
 * by the compiled "start" step right after {@code npc_move_to} itself ran (see {@code
 * SequenceCompiler}) — and resolves as {@link Result#CANCELLED} the moment it no longer matches {@code
 * NpcEntity#movementGeneration()}: some other MOVEMENT-channel command (another {@code npc_move_to},
 * {@code npc_follow_player}, or {@code npc_stop_move} — see {@code NpcEntity#beginMovementChannel})
 * has taken over navigation, so this wait ends instead of silently fighting the new command or
 * waiting forever.
 */
public final class NpcMoveToTask implements Task {

    /** The {@link Scope#INSTANCE} blackboard key the compiled "start" step writes the captured movement generation under. */
    public static final String GENERATION_KEY = "npc_move_to.generation";

    /** Close enough to the target to call this "arrived" — a small fixed radius, matching this codebase's other goal-proximity checks rather than deriving one from vanilla's own per-waypoint tolerance. */
    private static final double ARRIVAL_DISTANCE = 1.0;

    private final String npcName;
    private final Vec3 target;

    public NpcMoveToTask(String npcName, Vec3 target) {
        this.npcName = npcName;
        this.target = target;
    }

    @Override
    public Result tick(FlowContext context) {
        NpcEntity npc = NpcLookup.resolve(context.player().serverLevel(), npcName);
        if (npc == null) {
            return Result.FAILURE;
        }
        Long expectedGeneration = context.getVariable(Scope.INSTANCE, GENERATION_KEY);
        if (expectedGeneration == null || npc.movementGeneration() != expectedGeneration) {
            return Result.CANCELLED;
        }
        PathNavigation navigation = npc.getNavigation();
        if (!navigation.isDone()) {
            return Result.RUNNING;
        }
        boolean arrived = npc.distanceToSqr(target.x, target.y, target.z) <= ARRIVAL_DISTANCE * ARRIVAL_DISTANCE;
        return arrived ? Result.SUCCESS : Result.FAILURE;
    }

    @Override
    public void cancel(FlowContext context) {
        NpcEntity npc = NpcLookup.resolve(context.player().serverLevel(), npcName);
        if (npc != null) {
            npc.getNavigation().stop();
        }
    }
}
