package com.dimalab.storymodengine.common.flow.integration.persistence;

import com.dimalab.storymodengine.common.capabilities.CapabilityData;
import com.dimalab.storymodengine.api.capabilities.annotation.Capability;

/**
 * The one {@code @Capability} declaration Flow persistence needs — discovered automatically by the
 * already-existing {@code CapabilityDiscovery} (the same {@code ModFileScanData} scan that finds
 * every other {@code @Capability} field), with no Flow-specific bootstrap call required at all.
 */
public final class ModFlowCapabilities {

    @Capability(sync = true)
    public static final CapabilityData<StoryFlowData> FLOW_DATA = CapabilityData.of(StoryFlowData::new);
}
