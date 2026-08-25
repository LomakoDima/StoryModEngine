package com.dimalab.storymodengine.common.quest.objective;

import com.dimalab.storymodengine.common.flow.Flow;
import com.dimalab.storymodengine.common.quest.bridgeevent.BlockInteractionEvent;
import net.minecraft.resources.ResourceLocation;

/** {@code Objective.interact(blockId)} — right-click on any block of this type, anywhere. */
final class InteractObjective implements Objective {

    private final String id;
    private final ResourceLocation blockId;

    InteractObjective(ResourceLocation blockId) {
        this.id = "interact_" + ObjectiveSupport.sanitize(blockId);
        this.blockId = blockId;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String description() {
        return "Interact with " + blockId;
    }

    @Override
    public Flow compile(ResourceLocation questId) {
        return ObjectiveSupport.waitFor(questId, this, BlockInteractionEvent.class,
                (player, event) -> event.player().equals(player) && event.blockId().equals(blockId));
    }
}
