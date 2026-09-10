package com.dimalab.storymodengine.common.flow.wait;

import com.dimalab.storymodengine.api.capabilities.annotation.Capability;
import com.dimalab.storymodengine.common.capabilities.CapabilityData;

public final class PersistentWaitCapabilities {

    @Capability
    public static final CapabilityData<PersistentWaitData> PERSISTENT_WAIT = CapabilityData.of(PersistentWaitData::new);

    private PersistentWaitCapabilities() {
    }
}
