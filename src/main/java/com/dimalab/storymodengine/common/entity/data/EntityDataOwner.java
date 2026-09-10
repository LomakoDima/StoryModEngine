package com.dimalab.storymodengine.common.entity.data;

import com.dimalab.storymodengine.api.capabilities.EntityCapability;
import net.minecraft.nbt.CompoundTag;

/** {@code entity_set_data_* ... "owner"} — reaches only the owning client, and only when the owner is a player (see {@link com.dimalab.storymodengine.api.capabilities.SyncAudience#OWNER}). */
public final class EntityDataOwner implements EntityCapability {

    public CompoundTag tag = new CompoundTag();
}
