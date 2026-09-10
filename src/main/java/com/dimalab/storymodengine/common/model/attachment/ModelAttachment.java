package com.dimalab.storymodengine.common.model.attachment;

import com.dimalab.storymodengine.api.capabilities.EntityCapability;

/**
 * "This entity wears this glTF model instead of its own" — one model per entity, not a list. Plain
 * mutable class, public no-arg constructor, public non-final field: the exact shape every other
 * {@code @Capability} data type in this codebase already uses (see {@code
 * quest.persistence.QuestProgressData}'s own doc for why).
 *
 * <p>Deliberately separate from {@code NpcEntity}'s own {@code MODEL}/{@code SKIN_OWNER} {@code
 * SynchedEntityData} fields, not a replacement for them — see {@code ModelAttachCapabilities}' own
 * doc for why migrating {@code NpcEntity} onto this would be a real regression, not just a rename.
 * This is the path for every <em>other</em> entity: vanilla mobs, the real player, anything that
 * doesn't already have its own dedicated model field.
 */
public final class ModelAttachment implements EntityCapability {

    /** Empty (never null) means "no attachment" — mirrors {@code NpcEntity.modelName()}'s own convention. */
    public String modelName = "";
}
