package com.dimalab.storymodengine.common.model.attachment;

import com.dimalab.storymodengine.api.capabilities.annotation.Capability;
import com.dimalab.storymodengine.common.capabilities.CapabilityData;

/**
 * {@code sync = true} — an attached model is visual, so every client tracking the entity needs a
 * copy, not just the server. Discovered automatically by {@code CapabilityDiscovery}, same as every
 * other {@code @Capability} field in this codebase — nothing here registers anything by hand.
 */
public final class ModelAttachCapabilities {

    @Capability(sync = true)
    public static final CapabilityData<ModelAttachment> MODEL_ATTACHMENT = CapabilityData.of(ModelAttachment::new);

    private ModelAttachCapabilities() {
    }
}
