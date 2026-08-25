package com.dimalab.storymodengine.common.quest.persistence;

import com.dimalab.storymodengine.common.capabilities.CapabilityData;
import com.dimalab.storymodengine.api.capabilities.annotation.Capability;

/**
 * {@code sync = true} (unlike {@code cinematic.persistence.ModCinematicCapabilities}, which
 * deliberately leaves it {@code false}) — quest progress genuinely needs to reach the client, for
 * {@code quest.client.QuestTrackerOverlay}. Discovered automatically by {@code CapabilityDiscovery}
 * — no manual registration.
 */
public final class ModQuestCapabilities {

    @Capability(sync = true)
    public static final CapabilityData<QuestProgressData> PROGRESS_DATA = CapabilityData.of(QuestProgressData::new);

    private ModQuestCapabilities() {
    }
}
