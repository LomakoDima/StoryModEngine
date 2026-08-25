package com.dimalab.storymodengine.common.quest.persistence;

import com.dimalab.storymodengine.api.capabilities.EntityCapability;
import com.dimalab.storymodengine.common.quest.QuestProgress;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * One player's quest progress — active, completed, and failed quests all live in the same map,
 * distinguished by each entry's own {@link QuestProgress#state()} (there's no separate "completed
 * quests" list to keep in sync). Plain mutable class, public no-arg constructor, public non-final
 * field — the exact shape {@code cinematic.persistence.CutscenePlaybackData} already establishes for
 * a {@code @Capability} type; {@link QuestProgress} itself is an immutable record replaced wholesale
 * on every change (see its own Javadoc), never mutated in place.
 */
public final class QuestProgressData implements EntityCapability {
    public Map<ResourceLocation, QuestProgress> quests = new HashMap<>();
}
