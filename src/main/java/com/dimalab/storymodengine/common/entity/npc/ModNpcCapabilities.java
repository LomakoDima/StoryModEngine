package com.dimalab.storymodengine.common.entity.npc;

import com.dimalab.storymodengine.api.capabilities.annotation.Capability;
import com.dimalab.storymodengine.common.capabilities.CapabilityData;

public final class ModNpcCapabilities {

    /** Server-only bookkeeping — never displayed to a client, so no sync needed (matches {@code trigger.persistence.ModTriggerCapabilities}' own reasoning). */
    @Capability(sync = false)
    public static final CapabilityData<NpcNameIndex> NAME_INDEX = CapabilityData.of(NpcNameIndex::new);

    /** Which dropped items are already claimed by an {@code npc_collect_items} goal — see {@link ItemClaims}. */
    @Capability(sync = false)
    public static final CapabilityData<NpcItemClaims> ITEM_CLAIMS = CapabilityData.of(NpcItemClaims::new);

    private ModNpcCapabilities() {
    }
}
