package com.dimalab.storymodengine.common.scripting.persistence;

import com.dimalab.storymodengine.api.capabilities.annotation.Capability;
import com.dimalab.storymodengine.common.capabilities.CapabilityData;

/**
 * Same shape as {@code capabilities.example.ModCapabilities} — plain fields, discovered by the
 * existing {@code CapabilityDiscovery} scan (already running before {@code ScriptingBootstrap.init}
 * ever executes, since {@code CapabilityBootstrap} runs earlier in {@code EngineBootstrap.init}'s own
 * sequence), no separate registration call needed here. {@code sync = false} on both — re-verified
 * against {@code Capabilities.java}: syncing a {@code LEVEL}-owner capability is a documented no-op
 * today, and nothing in this MVP renders story variables client-side anyway.
 */
public final class ScriptingCapabilities {

    @Capability
    public static final CapabilityData<StoryVariableData> PLAYER_VARS = CapabilityData.of(StoryVariableData::new);

    @Capability
    public static final CapabilityData<StoryWorldVariableData> WORLD_VARS = CapabilityData.of(StoryWorldVariableData::new);

    private ScriptingCapabilities() {
    }
}
