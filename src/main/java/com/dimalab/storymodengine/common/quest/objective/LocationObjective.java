package com.dimalab.storymodengine.common.quest.objective;

import com.dimalab.storymodengine.common.flow.Flow;
import com.dimalab.storymodengine.common.flow.FlowContext;
import com.dimalab.storymodengine.api.quest.ObjectiveState;
import com.dimalab.storymodengine.common.quest.persistence.QuestProgressStore;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * {@code Objective.location(pos, radius)} — no Forge event fires when a player simply walks
 * somewhere, and broadcasting a global "player moved" event every tick for every player (whether or
 * not anyone has an active location objective) would be exactly the "постоянно сканировать мир без
 * необходимости" the task warns against. Instead, this polls only *while this specific objective on
 * this specific quest instance is active*, once per second, via {@link Flow#lazy}/{@link
 * Flow#branch} recursion — existing {@code Flow} primitives only, no new one added (see the design
 * doc §3/§9).
 */
final class LocationObjective implements Objective {

    private static final int CHECK_INTERVAL_TICKS = 20;

    private final String id;
    private final BlockPos target;
    private final double radius;

    LocationObjective(BlockPos target, double radius) {
        this.id = "location_" + target.getX() + "_" + target.getY() + "_" + target.getZ();
        this.target = target;
        this.radius = radius;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String description() {
        return "Reach " + target;
    }

    @Override
    public Flow compile(ResourceLocation questId) {
        return Flow.sequence(
                Flow.action(ctx -> QuestProgressStore.setObjectiveState(ctx.player(), questId, id, ObjectiveState.ACTIVE)),
                pollUntilReached(),
                Flow.action(ctx -> QuestProgressStore.completeObjective(ctx.player(), questId, id, requiredCount()))
        );
    }

    /** {@code Flow.lazy} defers building the recursive branch until it's actually reached, so this doesn't blow the stack constructing an "infinite" Flow up front. */
    private Flow pollUntilReached() {
        return Flow.lazy(() -> Flow.sequence(
                Flow.wait(CHECK_INTERVAL_TICKS),
                Flow.branch(this::isNear, Flow.action(ctx -> {
                }), pollUntilReached())
        ));
    }

    private Boolean isNear(FlowContext context) {
        Vec3 pos = context.player().position();
        double dx = pos.x - (target.getX() + 0.5);
        double dy = pos.y - (target.getY() + 0.5);
        double dz = pos.z - (target.getZ() + 0.5);
        return dx * dx + dy * dy + dz * dz <= radius * radius;
    }
}
