package com.dimalab.storymodengine.common.trigger.persistence;

import com.dimalab.storymodengine.api.capabilities.annotation.Capability;
import com.dimalab.storymodengine.common.capabilities.CapabilityData;

public final class ModTriggerCapabilities {

    /** Server-only bookkeeping — never displayed to a client, so no sync needed (matches {@code QuestProgressData}'s own reasoning for {@code sync}, but this data has no client-facing use at all, unlike quest progress). */
    @Capability(sync = false)
    public static final CapabilityData<TriggerPlayerStateData> PLAYER_STATE = CapabilityData.of(TriggerPlayerStateData::new);

    @Capability(sync = false)
    public static final CapabilityData<TriggerGlobalStateData> GLOBAL_STATE = CapabilityData.of(TriggerGlobalStateData::new);

    private ModTriggerCapabilities() {
    }
}
