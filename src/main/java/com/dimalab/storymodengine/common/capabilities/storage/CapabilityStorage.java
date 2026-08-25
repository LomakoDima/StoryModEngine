package com.dimalab.storymodengine.common.capabilities.storage;

import com.dimalab.storymodengine.common.capabilities.CapabilityLifecycle;
import com.dimalab.storymodengine.api.capabilities.OwnerKind;
import com.dimalab.storymodengine.common.capabilities.registry.CapabilityDescriptor;
import com.dimalab.storymodengine.common.capabilities.registry.CapabilityRegistry;
import com.dimalab.storymodengine.common.logging.EngineLog;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The real {@code ICapabilitySerializable} attached to one owner instance (one entity, one block
 * entity, or one level) — a typed bag holding every {@code @Capability} data instance registered
 * for that {@link OwnerKind}, keyed by data type. This is the engine's own answer to "{@code
 * CapabilityProvider}" from the task's suggested file list, renamed to avoid colliding with Forge's
 * own {@code net.minecraftforge.common.capabilities.CapabilityProvider} — see {@code
 * ARCHITECTURE.md} for the full reasoning (one real Forge {@code Capability<CapabilityStorage>}
 * for the whole engine, since {@code CapabilityToken}'s generic-capturing transformer only works
 * for one fixed type per JVM).
 *
 * <p>NBT and network payloads for each data instance are both produced by encoding the same {@code
 * Serializer<T>} (from {@link CapabilityDescriptor#serializer()}, itself resolved once by {@code
 * SerializerRegistry}) into a {@link FriendlyByteBuf}, then storing/sending the raw bytes — one
 * code path for disk and wire, deliberately, per the task's explicit instruction to reuse the
 * network serializer rather than build NBT-native serialization a second time. Each data type is
 * serialized independently, in its own try/catch, so a single corrupted or unreadable entry never
 * takes the rest of the owner's data down with it.
 */
public final class CapabilityStorage implements ICapabilitySerializable<CompoundTag> {

    private final OwnerKind ownerKind;
    private final Map<Class<?>, Object> instances = new LinkedHashMap<>();
    private LazyOptional<CapabilityStorage> self = LazyOptional.of(() -> this);

    public CapabilityStorage(OwnerKind ownerKind) {
        this.ownerKind = ownerKind;
        for (CapabilityDescriptor<?> descriptor : CapabilityRegistry.forOwner(ownerKind)) {
            instances.put(descriptor.dataType(), descriptor.factory().get());
        }
    }

    public OwnerKind ownerKind() {
        return ownerKind;
    }

    @SuppressWarnings("unchecked")
    public <T> T get(Class<T> dataType) {
        return (T) instances.get(dataType);
    }

    public <T> void set(Class<T> dataType, T value) {
        instances.put(dataType, value);
    }

    /** Copies every held instance from {@code other} — used for {@code PlayerEvent.Clone} (death/dimension change). */
    public void copyFrom(CapabilityStorage other) {
        instances.putAll(other.instances);
    }

    public void invalidate() {
        self.invalidate();
    }

    @Override
    @NotNull
    public <T> LazyOptional<T> getCapability(net.minecraftforge.common.capabilities.@NotNull Capability<T> cap, @Nullable Direction side) {
        // Entity#invalidateCaps() permanently kills a LazyOptional (verified against Forge source —
        // there is no "un-invalidate"), but Forge calls it on *every* removal, including the
        // same-instance CHANGED_DIMENSION case (portal travel) and the revive-copy window around
        // PlayerEvent.Clone — both of which are meant to keep working afterwards via reviveCaps().
        // reviveCaps() only flips the entity-level gate, not our own already-dead `self`, so it must
        // be recreated lazily here; the underlying `instances` map is untouched by invalidate(), so
        // no data is lost by doing this.
        if (!self.isPresent()) {
            self = LazyOptional.of(() -> this);
        }
        return CapabilityLifecycle.CAPABILITY.orEmpty(cap, self);
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag root = new CompoundTag();
        for (CapabilityDescriptor<?> descriptor : CapabilityRegistry.forOwner(ownerKind)) {
            Object value = instances.get(descriptor.dataType());
            if (value == null) {
                continue;
            }
            try {
                root.putByteArray(descriptor.id().toString(), encode(descriptor, value));
            } catch (Exception e) {
                EngineLog.channel("Capabilities").error(
                        "Failed to serialize {} to NBT: {}", descriptor.id(), e.toString());
            }
        }
        return root;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        for (CapabilityDescriptor<?> descriptor : CapabilityRegistry.forOwner(ownerKind)) {
            String key = descriptor.id().toString();
            if (!nbt.contains(key)) {
                continue;
            }
            try {
                instances.put(descriptor.dataType(), descriptor.serializer().fromBytes(nbt.getByteArray(key)));
                EngineLog.channel("Capabilities").trace("Loaded {}", descriptor.id());
            } catch (Exception e) {
                EngineLog.channel("Capabilities").error(
                        "Failed to deserialize {}, keeping default value: {}", descriptor.id(), e.toString());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> byte[] encode(CapabilityDescriptor<T> descriptor, Object value) {
        return descriptor.serializer().toBytes((T) value);
    }
}
