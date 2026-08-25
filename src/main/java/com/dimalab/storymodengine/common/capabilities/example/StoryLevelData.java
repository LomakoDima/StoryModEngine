package com.dimalab.storymodengine.common.capabilities.example;

import com.dimalab.storymodengine.api.capabilities.LevelCapability;

/**
 * Exercises the {@code Level} owner kind specifically — see {@link StoryBlockData}'s Javadoc for
 * why this exists alongside the task's own player-focused example. Persisted per-dimension via
 * Forge's {@code LevelCapabilityData}/{@code SavedData} (see {@code ARCHITECTURE.md}).
 */
public final class StoryLevelData implements LevelCapability {

    public int worldEventsTriggered;
}
