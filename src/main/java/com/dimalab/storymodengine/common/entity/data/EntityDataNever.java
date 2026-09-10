package com.dimalab.storymodengine.common.entity.data;

import com.dimalab.storymodengine.api.capabilities.EntityCapability;
import net.minecraft.nbt.CompoundTag;

/** {@code entity_set_data_* ... "never"} — server-only bookkeeping, never pushed to any client. */
public final class EntityDataNever implements EntityCapability {

    public CompoundTag tag = new CompoundTag();
}
