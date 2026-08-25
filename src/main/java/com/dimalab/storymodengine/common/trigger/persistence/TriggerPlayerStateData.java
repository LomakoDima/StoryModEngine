package com.dimalab.storymodengine.common.trigger.persistence;

import com.dimalab.storymodengine.api.capabilities.EntityCapability;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.Set;

/** Which {@code persistent()} {@code ONCE} triggers have already fired for this player — see {@code TriggerStateStore}. */
public final class TriggerPlayerStateData implements EntityCapability {

    public Set<ResourceLocation> fired = new HashSet<>();
}
