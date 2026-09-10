package com.dimalab.storymodengine.common.entity.data;

import com.dimalab.storymodengine.api.capabilities.SyncAudience;
import com.dimalab.storymodengine.api.capabilities.annotation.Capability;
import com.dimalab.storymodengine.common.capabilities.CapabilityData;

/**
 * Three sync tiers for {@code entity.data}, one capability per tier rather than one capability with
 * an internal sync flag — a syncable value has to be a whole {@code @Capability} entry on its own
 * (see {@code Capabilities.sync}), so "never syncs" and "syncs to everyone tracking" genuinely can't
 * share one descriptor. Attaches to any {@code Entity} for free, same as {@code ModelAttachment} did
 * this session — {@code CapabilityLifecycle}'s {@code AttachCapabilitiesEvent<Entity>} subscription is
 * already entity-type-agnostic.
 */
public final class EntityDataCapabilities {

    @Capability
    public static final CapabilityData<EntityDataNever> NEVER = CapabilityData.of(EntityDataNever::new);

    @Capability(sync = true, audience = SyncAudience.OWNER)
    public static final CapabilityData<EntityDataOwner> OWNER = CapabilityData.of(EntityDataOwner::new);

    @Capability(sync = true, audience = SyncAudience.TRACKING)
    public static final CapabilityData<EntityDataTracking> TRACKING = CapabilityData.of(EntityDataTracking::new);

    private EntityDataCapabilities() {
    }
}
