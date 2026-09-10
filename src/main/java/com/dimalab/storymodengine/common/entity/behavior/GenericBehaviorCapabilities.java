package com.dimalab.storymodengine.common.entity.behavior;

import com.dimalab.storymodengine.api.capabilities.annotation.Capability;
import com.dimalab.storymodengine.common.capabilities.CapabilityData;

/**
 * No {@code sync = true} on either — unlike {@code ModelAttachment} (which changes what the client
 * renders), nothing here needs to reach the client directly: the walking/turning these drive is already
 * visible through vanilla's own entity-tracking position/rotation packets. Same server-only reasoning
 * {@code NpcItemClaims} already uses. Discovered automatically by {@code CapabilityDiscovery}, same as
 * every other {@code @Capability} field in this codebase — nothing here registers anything by hand.
 */
public final class GenericBehaviorCapabilities {

    @Capability
    public static final CapabilityData<FollowBehavior> FOLLOW = CapabilityData.of(FollowBehavior::new);

    @Capability
    public static final CapabilityData<LookAtBehavior> LOOK_AT = CapabilityData.of(LookAtBehavior::new);

    private GenericBehaviorCapabilities() {
    }
}
