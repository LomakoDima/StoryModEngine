package com.dimalab.storymodengine.common.trigger.persistence;

import com.dimalab.storymodengine.api.capabilities.LevelCapability;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.Set;

/**
 * Which {@code persistent()} {@code ONCE} triggers of global scope (a {@code TIME} trigger, or an
 * {@code EVENT} trigger whose event carries no identifiable player) have already fired, server-wide
 * — see {@code TriggerStateStore}. Attached to the overworld specifically ({@code TriggerStateStore}
 * always reads/writes {@code server.overworld()}), the same "one canonical world" choice a global
 * flag needs regardless of which dimension the firing condition happened to be checked in.
 */
public final class TriggerGlobalStateData implements LevelCapability {

    public Set<ResourceLocation> fired = new HashSet<>();
}
