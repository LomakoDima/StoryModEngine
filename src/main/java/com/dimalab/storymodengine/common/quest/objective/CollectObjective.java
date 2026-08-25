package com.dimalab.storymodengine.common.quest.objective;

import com.dimalab.storymodengine.common.flow.Flow;
import com.dimalab.storymodengine.common.quest.bridgeevent.ItemCollectedEvent;
import net.minecraft.resources.ResourceLocation;

/** {@code Objective.collect(itemId, count)} — ground pickups only, see {@code ItemCollectedEvent}'s own Javadoc for the documented scope limit. */
final class CollectObjective implements Objective {

    private final String id;
    private final ResourceLocation itemId;
    private final int requiredCount;

    CollectObjective(ResourceLocation itemId, int requiredCount) {
        this.id = "collect_" + ObjectiveSupport.sanitize(itemId);
        this.itemId = itemId;
        this.requiredCount = requiredCount;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String description() {
        return "Collect " + requiredCount + "x " + itemId;
    }

    @Override
    public int requiredCount() {
        return requiredCount;
    }

    @Override
    public Flow compile(ResourceLocation questId) {
        return ObjectiveSupport.waitFor(questId, this, ItemCollectedEvent.class,
                (player, event) -> event.player().equals(player) && event.itemId().equals(itemId),
                event -> event.count());
    }
}
