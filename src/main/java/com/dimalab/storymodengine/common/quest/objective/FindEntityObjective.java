package com.dimalab.storymodengine.common.quest.objective;

import com.dimalab.storymodengine.common.flow.Flow;
import com.dimalab.storymodengine.common.flow.FlowContext;
import com.dimalab.storymodengine.common.logging.EngineLog;
import com.dimalab.storymodengine.api.quest.ObjectiveState;
import com.dimalab.storymodengine.common.quest.persistence.QuestProgressStore;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * {@code Objective.findEntity(entityTypeId, radius)} — "get near a live entity of this type," as
 * distinct from {@link LocationObjective} (fixed coordinates) and {@link KillObjective} (must kill
 * it). Same {@code Flow.lazy}/{@code Flow.branch} polling technique as {@link LocationObjective},
 * for the same reason (no natural Forge "entity is nearby" event to hook).
 */
final class FindEntityObjective implements Objective {

    private static final int CHECK_INTERVAL_TICKS = 20;

    private final String id;
    private final ResourceLocation entityTypeId;
    private final double radius;

    FindEntityObjective(ResourceLocation entityTypeId, double radius) {
        this.id = "find_" + ObjectiveSupport.sanitize(entityTypeId);
        this.entityTypeId = entityTypeId;
        this.radius = radius;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String description() {
        return "Find " + entityTypeId;
    }

    @Override
    public Flow compile(ResourceLocation questId) {
        return Flow.sequence(
                Flow.action(ctx -> QuestProgressStore.setObjectiveState(ctx.player(), questId, id, ObjectiveState.ACTIVE)),
                pollUntilFound(),
                Flow.action(ctx -> QuestProgressStore.completeObjective(ctx.player(), questId, id, requiredCount()))
        );
    }

    private Flow pollUntilFound() {
        return Flow.lazy(() -> Flow.sequence(
                Flow.wait(CHECK_INTERVAL_TICKS),
                Flow.branch(this::isNearby, Flow.action(ctx -> {
                }), pollUntilFound())
        ));
    }

    private Boolean isNearby(FlowContext context) {
        EntityType<?> entityType = ForgeRegistries.ENTITY_TYPES.getValue(entityTypeId);
        if (entityType == null) {
            EngineLog.channel("Quest").warn("Objective.findEntity({}): unknown entity type", entityTypeId);
            return false;
        }
        ServerPlayer player = context.player();
        AABB box = player.getBoundingBox().inflate(radius);
        return !player.serverLevel().getEntitiesOfClass(Entity.class, box, e -> e.getType() == entityType).isEmpty();
    }
}
