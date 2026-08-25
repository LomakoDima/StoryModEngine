package com.dimalab.storymodengine.common.capabilities.example;

import com.dimalab.storymodengine.api.capabilities.BlockEntityCapability;

/**
 * Exercises the {@code BlockEntity} owner kind specifically — not part of the task's own player
 * example, but attachment/persistence needs a real declaration to actually run against, the same
 * way {@link StoryPlayerData} exercises {@code Entity} and {@link StoryLevelData} exercises {@code
 * Level}. Every block entity in the game gets one of these (harmless — a single {@code int}).
 */
public final class StoryBlockData implements BlockEntityCapability {

    public int interactions;
}
