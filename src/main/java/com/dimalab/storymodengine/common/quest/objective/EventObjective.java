package com.dimalab.storymodengine.common.quest.objective;

import com.dimalab.storymodengine.api.event.Event;
import com.dimalab.storymodengine.common.flow.Flow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.BiPredicate;

/**
 * {@code Objective.event(id, description, EventType.class, matches)} — the direct escape hatch onto
 * {@link ObjectiveSupport#waitFor} for anything the 8 named objective kinds don't cover, without
 * writing a whole new {@link Objective} implementation. Requires an explicit {@code id} (unlike the
 * named factories) since an arbitrary event type + predicate has no content to derive one from.
 */
final class EventObjective<E extends Event> implements Objective {

    private final String id;
    private final String description;
    private final Class<E> eventType;
    private final BiPredicate<ServerPlayer, E> matches;
    private final int requiredCount;

    EventObjective(String id, String description, Class<E> eventType, BiPredicate<ServerPlayer, E> matches, int requiredCount) {
        this.id = id;
        this.description = description;
        this.eventType = eventType;
        this.matches = matches;
        this.requiredCount = requiredCount;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public int requiredCount() {
        return requiredCount;
    }

    @Override
    public Flow compile(ResourceLocation questId) {
        return ObjectiveSupport.waitFor(questId, this, eventType, matches);
    }
}
