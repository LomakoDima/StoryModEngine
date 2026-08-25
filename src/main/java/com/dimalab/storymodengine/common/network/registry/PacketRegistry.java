package com.dimalab.storymodengine.common.network.registry;

import com.dimalab.storymodengine.api.network.PacketDirection;
import com.dimalab.storymodengine.common.network.serialization.PacketSerializer;

import java.util.ArrayList;
import java.util.List;

/**
 * The packets discovered for one mod's channel — instance-scoped, not static, because packet
 * discriminators are only unique <em>within</em> a channel and every mod using this engine gets
 * its own channel (mirrors {@code ResourcePacks}/{@code AssetGenerator} being per-{@code modId}
 * rather than global, for the same reason). {@code PacketDiscovery} builds one of these per mod;
 * {@code NetworkBootstrap} is what actually feeds its {@link PacketDescriptor}s into a {@code
 * SimpleChannel}.
 *
 * <p>{@code IndexedMessageCodec}'s discriminator is a single unsigned byte (verified against
 * source — {@code payload.readUnsignedByte()}), so a channel holds at most 256 packet types;
 * {@link #register} throws once that's exhausted rather than silently wrapping ids.
 */
public final class PacketRegistry {

    private static final int MAX_PACKETS = 256;

    private final List<PacketDescriptor<?>> descriptors = new ArrayList<>();

    /** Assigns the next id and builds the record's {@link com.dimalab.storymodengine.common.network.serialization.Serializer} via {@link PacketSerializer}. */
    public <T> PacketDescriptor<T> register(Class<T> type, PacketDirection direction) {
        if (descriptors.size() >= MAX_PACKETS) {
            throw new IllegalStateException(
                    "Cannot register " + type.getName() + " — this channel already has "
                            + MAX_PACKETS + " packets, the maximum SimpleChannel's single-byte "
                            + "discriminator supports");
        }
        PacketDescriptor<T> descriptor = new PacketDescriptor<>(
                descriptors.size(), type, PacketSerializer.forRecord(type), direction);
        descriptors.add(descriptor);
        return descriptor;
    }

    public List<PacketDescriptor<?>> all() {
        return List.copyOf(descriptors);
    }
}
