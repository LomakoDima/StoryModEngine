package com.dimalab.storymodengine.common.entity.data;

import com.dimalab.storymodengine.common.network.serialization.Serializer;
import com.dimalab.storymodengine.common.network.serialization.SerializerRegistry;
import net.minecraft.nbt.CompoundTag;

/**
 * Registers a hand-written {@code Serializer<CompoundTag>} — the reflective {@code PojoSerializer}
 * only resolves this engine's own closed primitive/collection universe, never a raw NBT type, so a
 * capability POJO with a {@code CompoundTag} field needs this registered before {@code
 * CapabilityBootstrap} resolves it, the same ordering constraint {@code FlowState.registerSerializer()}
 * documents for itself. {@code FriendlyByteBuf.writeNbt}/{@code readNbt} already handle an empty tag
 * (used as {@code null}) correctly, so no extra null-guarding is needed here.
 */
public final class EntityDataSerializers {

    private EntityDataSerializers() {
    }

    public static void registerSerializer() {
        SerializerRegistry.register(CompoundTag.class, Serializer.of(
                (CompoundTag value, net.minecraft.network.FriendlyByteBuf buf) -> buf.writeNbt(value),
                buf -> {
                    CompoundTag tag = buf.readNbt();
                    return tag != null ? tag : new CompoundTag();
                }));
    }
}
