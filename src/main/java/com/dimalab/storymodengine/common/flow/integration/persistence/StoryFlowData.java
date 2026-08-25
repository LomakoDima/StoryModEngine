package com.dimalab.storymodengine.common.flow.integration.persistence;

import com.dimalab.storymodengine.api.capabilities.EntityCapability;
import com.dimalab.storymodengine.common.flow.FlowState;

import java.util.HashMap;
import java.util.Map;

/**
 * Persistent per-player Flow bookkeeping — reuses the existing {@code capabilities} system
 * entirely, no second persistence path. Keyed by {@code flowId.toString()} so a player can have
 * several independent flows active at once (e.g. a main story flow and a side flow).
 * {@code FlowManager} keeps {@link #activeFlows} up to date in memory on every meaningful
 * transition; actual NBT persistence is the same automatic {@code Entity#serializeCaps}/{@code
 * deserializeCaps} mechanism every other {@code @Capability} already gets for free — nothing here
 * triggers a save by hand.
 */
public final class StoryFlowData implements EntityCapability {

    public Map<String, FlowState> activeFlows = new HashMap<>();
}
