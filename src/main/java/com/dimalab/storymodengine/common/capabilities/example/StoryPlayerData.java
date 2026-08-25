package com.dimalab.storymodengine.common.capabilities.example;

import com.dimalab.storymodengine.api.capabilities.EntityCapability;

/**
 * The task's own worked example — persistent, per-player story progress. A plain mutable class
 * (not a record: {@code data.storyPoints++} needs a settable field), {@code implements
 * EntityCapability} so {@code CapabilityDiscovery} attaches one instance to every {@code Entity}
 * (players included — see {@code ARCHITECTURE.md} for why there's no separate player-only owner
 * kind). Serialized automatically by {@code PojoSerializer} — no {@code serializeNBT}/{@code
 * deserializeNBT} written here, nor anywhere else.
 */
public final class StoryPlayerData implements EntityCapability {

    public int storyPoints;
    public boolean metTheGuide;
    public int questsCompleted;
}
