package com.dimalab.storymodengine.common.entity.data;

import com.dimalab.storymodengine.api.capabilities.EntityCapability;
import net.minecraft.nbt.CompoundTag;

/** {@code entity_set_data_* ... "tracking"} — reaches every client currently tracking the owner entity. */
public final class EntityDataTracking implements EntityCapability {

    public CompoundTag tag = new CompoundTag();
}
