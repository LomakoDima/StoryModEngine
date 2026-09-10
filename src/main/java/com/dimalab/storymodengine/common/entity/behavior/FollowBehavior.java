package com.dimalab.storymodengine.common.entity.behavior;

import com.dimalab.storymodengine.api.capabilities.EntityCapability;

import java.util.Optional;
import java.util.UUID;

/**
 * "This entity walks toward this player" — the generic, any-{@code Mob} equivalent of {@code
 * NpcEntity}'s own {@code followTarget} field (see {@code NpcFollowGoal}), for a mob that isn't an
 * {@code NpcEntity} and so has no goal of its own this engine can add to it (see {@code
 * GenericBehaviorSystem}'s own doc for why this is driven by a tick scan instead of a real {@code
 * Goal}). Same shape every other {@code @Capability} data type in this codebase uses: plain mutable
 * class, public no-arg constructor, public non-final fields.
 *
 * <p>{@code targetId} is {@code Optional<UUID>}, not a plain {@code @Nullable UUID} — confirmed the
 * hard way (a real in-game NBT-encode crash spamming every autosave): {@code SerializerRegistry}'s own
 * doc names {@code Optional<T>} as "this engine's nullable-value convention," and {@code UUID.class}'s
 * registered serializer (`MinecraftSerializers`) always calls {@code FriendlyByteBuf.writeUUID}
 * unconditionally, with no null check — a raw null {@code UUID} field blows up the moment any
 * capability holding it gets saved. {@code CutscenePlaybackData.activeCutscene} is the existing
 * precedent for this exact pattern ({@code Optional<T> field = Optional.empty();}), not a new one.
 */
public final class FollowBehavior implements EntityCapability {

    /** Player to walk toward, or {@code Optional.empty()} for "not following anything." */
    public Optional<UUID> targetId = Optional.empty();

    /** {@link GenericBehaviorSystem}'s own repath-cadence bookkeeping — not script-facing. */
    public int repathCooldown = 0;
}
