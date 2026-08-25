package com.dimalab.storymodengine.common.quest.objective;

import com.dimalab.storymodengine.common.flow.Flow;
import com.dimalab.storymodengine.common.quest.bridgeevent.EntityKilledEvent;
import net.minecraft.resources.ResourceLocation;

/** {@code Objective.kill(entityTypeId, count)} — see {@code event.bridge.MinecraftEventBridge#onLivingDeath} for the underlying event source. */
final class KillObjective implements Objective {

    private final String id;
    private final ResourceLocation entityTypeId;
    private final int requiredCount;

    KillObjective(ResourceLocation entityTypeId, int requiredCount) {
        this.id = "kill_" + ObjectiveSupport.sanitize(entityTypeId);
        this.entityTypeId = entityTypeId;
        this.requiredCount = requiredCount;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String description() {
        return "Kill " + requiredCount + "x " + entityTypeId;
    }

    @Override
    public int requiredCount() {
        return requiredCount;
    }

    @Override
    public Flow compile(ResourceLocation questId) {
        return ObjectiveSupport.waitFor(questId, this, EntityKilledEvent.class,
                (player, event) -> event.killer().equals(player) && event.entityTypeId().equals(entityTypeId));
    }
}
