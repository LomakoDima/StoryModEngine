package com.dimalab.storymodengine.common.model.attachment;

import com.dimalab.storymodengine.api.capabilities.annotation.Capability;
import com.dimalab.storymodengine.common.capabilities.CapabilityData;

/**
 * {@code sync = true} — same reasoning {@link ModelAttachCapabilities} already uses: this changes what
 * the client renders, so every client tracking the entity needs a copy, not just the server. Discovered
 * automatically by {@code CapabilityDiscovery}, same as every other {@code @Capability} field in this
 * codebase — nothing here registers anything by hand.
 */
public final class MaterialOverrideCapabilities {

    @Capability(sync = true)
    public static final CapabilityData<MaterialOverrides> OVERRIDES = CapabilityData.of(MaterialOverrides::new);

    private MaterialOverrideCapabilities() {
    }
}
