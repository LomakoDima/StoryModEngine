package com.dimalab.storymodengine.common.quest.objective;

import com.dimalab.storymodengine.common.flow.Flow;
import com.dimalab.storymodengine.common.quest.bridgeevent.EntityInteractionEvent;
import net.minecraft.resources.ResourceLocation;

/** {@code Objective.talkTo(entityTypeId)} — a plain "right-clicked this entity" trigger, deliberately simpler than {@link DialogueObjective} (which actually runs a full Dialogue). */
final class TalkToObjective implements Objective {

    private final String id;
    private final ResourceLocation entityTypeId;

    TalkToObjective(ResourceLocation entityTypeId) {
        this.id = "talkTo_" + ObjectiveSupport.sanitize(entityTypeId);
        this.entityTypeId = entityTypeId;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String description() {
        return "Talk to " + entityTypeId;
    }

    @Override
    public Flow compile(ResourceLocation questId) {
        return ObjectiveSupport.waitFor(questId, this, EntityInteractionEvent.class,
                (player, event) -> event.player().equals(player) && event.entityTypeId().equals(entityTypeId));
    }
}
