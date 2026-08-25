package com.dimalab.storymodengine.common.capabilities.sync;

import com.dimalab.storymodengine.common.capabilities.CapabilityLifecycle;
import com.dimalab.storymodengine.common.capabilities.registry.CapabilityDescriptor;
import com.dimalab.storymodengine.common.capabilities.registry.CapabilityRegistry;
import com.dimalab.storymodengine.common.capabilities.storage.CapabilityStorage;
import com.dimalab.storymodengine.common.logging.EngineLog;
import com.dimalab.storymodengine.api.network.ClientboundPacket;
import com.dimalab.storymodengine.api.network.PacketHandler;
import com.dimalab.storymodengine.api.network.annotation.Packet;
import com.dimalab.storymodengine.common.network.context.PacketContext;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * Server → client half of {@code Capabilities.sync}: one data type's current authoritative value,
 * for one entity, as bytes encoded by that data type's own {@code Serializer} — the exact same
 * encoding {@code CapabilityStorage} uses for NBT (see its Javadoc). Only ever sent for a {@code
 * @Capability(sync = true)} entry; the server decides when to send it, never the client — see
 * {@code ARCHITECTURE.md}'s security model for why there is deliberately no packet in the other
 * direction that carries a data *value*.
 */
@Packet
public record CapabilitySyncPacket(ResourceLocation dataId, int targetEntityId, byte[] payload)
        implements ClientboundPacket, PacketHandler {

    @Override
    public void handle(PacketContext context) {
        Level level = context.level();
        Entity target = level != null ? level.getEntity(targetEntityId) : null;
        if (target == null) {
            EngineLog.channel("Capabilities").debug(
                    "Sync for {} arrived for an entity ({}) that isn't loaded here, dropping", dataId, targetEntityId);
            return;
        }

        CapabilityDescriptor<?> descriptor = CapabilityRegistry.byId(dataId);
        if (descriptor == null) {
            EngineLog.channel("Capabilities").warn("Sync for unknown data id {}, dropping", dataId);
            return;
        }

        target.getCapability(CapabilityLifecycle.CAPABILITY).ifPresent(storage -> apply(storage, descriptor));
    }

    @SuppressWarnings("unchecked")
    private <T> void apply(CapabilityStorage storage, CapabilityDescriptor<T> descriptor) {
        try {
            T value = descriptor.serializer().fromBytes(payload);
            storage.set(descriptor.dataType(), value);
            EngineLog.channel("Capabilities").debug("Synchronized {} ← SERVER", dataId);
        } catch (Exception e) {
            EngineLog.channel("Capabilities").warn("Malformed sync payload for {}, dropping: {}", dataId, e.toString());
        }
    }
}
