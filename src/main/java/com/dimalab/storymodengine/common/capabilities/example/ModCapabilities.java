package com.dimalab.storymodengine.common.capabilities.example;

import com.dimalab.storymodengine.common.capabilities.CapabilityData;
import com.dimalab.storymodengine.api.capabilities.annotation.Capability;

/**
 * A mod author's own capability class — same shape as {@link com.dimalab.storymodengine.common.ModItems}/
 * {@link com.dimalab.storymodengine.common.ModEffects}: plain Java, {@code @Capability} infers the id
 * ({@code storymodengine:story_data}) from the field name and the owner kind from each data
 * class's own marker interface. {@code BLOCK_DATA}/{@code LEVEL_DATA} aren't part of the task's
 * player-focused example — they exist so {@code BlockEntity}/{@code Level} attachment has a real
 * declaration to actually run against (see {@link StoryBlockData}/{@link StoryLevelData}).
 */
public final class ModCapabilities {

    @Capability(sync = true)
    public static final CapabilityData<StoryPlayerData> STORY_DATA = CapabilityData.of(StoryPlayerData::new);

    @Capability
    public static final CapabilityData<StoryBlockData> BLOCK_DATA = CapabilityData.of(StoryBlockData::new);

    @Capability
    public static final CapabilityData<StoryLevelData> LEVEL_DATA = CapabilityData.of(StoryLevelData::new);
}
