package com.dimalab.storymodengine.common.cinematic.persistence;

import com.dimalab.storymodengine.api.capabilities.EntityCapability;

import java.util.Optional;

/**
 * Persistent per-player cutscene bookkeeping — reuses the existing {@code capabilities} system
 * entirely, no second persistence path (mirrors {@code flow.integration.persistence.StoryFlowData}
 * exactly). A single {@code Optional} field, not a map keyed by id like {@code StoryFlowData}'s:
 * {@code CinematicManager} already enforces "one active cutscene per player," so a map would only
 * ever hold at most one entry — this says that directly instead of imitating a shape that doesn't
 * apply here. {@link #activeCutscene} is {@code Optional}, not a raw nullable field, because
 * that's this engine's own nullable-value serialization convention (see {@code
 * SerializerRegistry}'s class doc) — a raw {@code null} here breaks {@code PojoSerializer}, which
 * calls straight through to the field's value with no null guard. {@code CinematicManager} keeps
 * {@link #activeCutscene} up to date (write-through, not on every tick — see its own Javadoc for
 * why); NBT persistence itself is the same automatic {@code Entity#serializeCaps}/{@code
 * deserializeCaps} mechanism every other {@code @Capability} gets for free.
 */
public final class CutscenePlaybackData implements EntityCapability {

    public Optional<PersistedCutscene> activeCutscene = Optional.empty();
}
