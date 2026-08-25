package com.dimalab.storymodengine.common.network;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.Optional;

/**
 * A packet-safe reference to an {@link Entity} — its network id, nothing more. This, not {@code
 * Entity} itself, is what "Entity references" means for automatic serialization: {@code
 * SimpleChannel}'s decoder is a plain {@code Function<FriendlyByteBuf, MSG>} (verified against
 * source) with no {@code Level} to resolve an id against, on either side — the server may have
 * several, and resolving blind, without knowing which {@code Level} or whether the entity is even
 * the one the sender meant, is exactly the kind of automatic-for-convenience unsafety this engine
 * avoids by design. Resolution happens explicitly in {@link PacketHandler#handle}, where {@link
 * PacketContext#level()} already has the right answer for the receiving side.
 *
 * <pre>{@code
 * @Override
 * public void handle(PacketContext context) {
 *     targetEntity.resolve(context.level()).ifPresent(entity -> ...);
 * }
 * }</pre>
 */
public record EntityRef(int id) {

    public static EntityRef of(Entity entity) {
        return new EntityRef(entity.getId());
    }

    /** Looks the id up in {@code level} — empty if it's not loaded there (already gone, wrong level, or never valid). */
    public Optional<Entity> resolve(Level level) {
        return Optional.ofNullable(level.getEntity(id));
    }
}
