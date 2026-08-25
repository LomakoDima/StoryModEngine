package com.dimalab.storymodengine.common.cinematic.persistence;

import com.dimalab.storymodengine.common.capabilities.CapabilityData;
import com.dimalab.storymodengine.api.capabilities.annotation.Capability;

/**
 * The one {@code @Capability} declaration cutscene persistence needs — discovered automatically by
 * the already-existing {@code CapabilityDiscovery}, no cinematic-specific bootstrap call required.
 * {@code sync} is left at its default ({@code false}) — this is server-only bookkeeping the client
 * never needs a copy of (it learns about a resumed cutscene the normal way, via {@code
 * PlayCutscenePacket}, not by reading this capability).
 */
public final class ModCinematicCapabilities {

    @Capability
    public static final CapabilityData<CutscenePlaybackData> PLAYBACK_DATA = CapabilityData.of(CutscenePlaybackData::new);
}
