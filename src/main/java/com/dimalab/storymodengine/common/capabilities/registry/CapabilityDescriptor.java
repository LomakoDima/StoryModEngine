package com.dimalab.storymodengine.common.capabilities.registry;

import com.dimalab.storymodengine.api.capabilities.OwnerKind;
import com.dimalab.storymodengine.common.network.serialization.Serializer;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Supplier;

/**
 * Everything {@code CapabilityDiscovery} inferred about one {@code @Capability} field: its id
 * (namespaced by the declaring mod, path from the field name — same convention {@code
 * ContentIds}/{@code @AutoContent} already use), the data type's construction recipe, which kind
 * of object it attaches to, whether it may be synchronized to clients, and the {@link Serializer}
 * that reads/writes it (resolved once, from {@code SerializerRegistry}, and reused for both NBT
 * persistence and network sync — see {@code ARCHITECTURE.md}).
 */
public record CapabilityDescriptor<T>(
        ResourceLocation id,
        Class<T> dataType,
        Supplier<T> factory,
        OwnerKind ownerKind,
        boolean sync,
        Serializer<T> serializer
) {
}
