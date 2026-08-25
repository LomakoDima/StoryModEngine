package com.dimalab.storymodengine.common.capabilities.registry;

import com.dimalab.storymodengine.api.capabilities.OwnerKind;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Every {@code @Capability} field discovered so far, across every mod that has called {@code
 * CapabilityBootstrap.init(...)} — a single, global registry, deliberately not scoped per mod the
 * way {@code network.registry.PacketRegistry} is. This isn't a stylistic choice: Forge's {@code
 * Capability<T>} identity is keyed by the string name of the Java type {@code T} (verified against
 * {@code CapabilityManager}/{@code CapabilityToken} source — the generic-capturing transformer
 * only works for a fixed type at a fixed source location), so there can only ever be <em>one</em>
 * {@code Capability<CapabilityStorage>} per JVM regardless of how many mods use this engine — every
 * mod's data classes end up living inside the same kind of {@code CapabilityStorage}, keyed by
 * their own namespaced {@link CapabilityDescriptor#id()}. A global registry is simply the correct
 * shape for that reality, the same way {@code content.ContentDiscovery}'s own discovered-content
 * list is global rather than per-mod.
 */
public final class CapabilityRegistry {

    private static final List<CapabilityDescriptor<?>> ALL = new CopyOnWriteArrayList<>();
    private static final Map<Class<?>, CapabilityDescriptor<?>> BY_TYPE = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, CapabilityDescriptor<?>> BY_ID = new ConcurrentHashMap<>();
    private static final Map<OwnerKind, List<CapabilityDescriptor<?>>> BY_OWNER = new ConcurrentHashMap<>();

    private CapabilityRegistry() {
    }

    public static synchronized void register(CapabilityDescriptor<?> descriptor) {
        ALL.add(descriptor);
        BY_TYPE.put(descriptor.dataType(), descriptor);
        BY_ID.put(descriptor.id(), descriptor);
        BY_OWNER.computeIfAbsent(descriptor.ownerKind(), k -> new CopyOnWriteArrayList<>()).add(descriptor);
    }

    public static List<CapabilityDescriptor<?>> all() {
        return Collections.unmodifiableList(ALL);
    }

    /** Every descriptor targeting {@code kind} — what {@code CapabilityStorage} populates itself from at attach time. */
    public static List<CapabilityDescriptor<?>> forOwner(OwnerKind kind) {
        return BY_OWNER.getOrDefault(kind, List.of());
    }

    @SuppressWarnings("unchecked")
    public static <T> CapabilityDescriptor<T> forType(Class<T> dataType) {
        return (CapabilityDescriptor<T>) BY_TYPE.get(dataType);
    }

    /** Looked up by network sync packets, which only carry the compact {@link ResourceLocation} id on the wire. */
    public static CapabilityDescriptor<?> byId(ResourceLocation id) {
        return BY_ID.get(id);
    }

    public static boolean isEmpty() {
        return ALL.isEmpty();
    }
}
