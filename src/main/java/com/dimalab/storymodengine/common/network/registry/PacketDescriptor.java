package com.dimalab.storymodengine.common.network.registry;

import com.dimalab.storymodengine.api.network.PacketDirection;
import com.dimalab.storymodengine.common.network.serialization.Serializer;

/**
 * Everything {@code PacketDiscovery} inferred about one {@code @Packet} record: its discriminator
 * {@code id} within the owning mod's channel (0..255 — see {@code PacketRegistry}), the composed
 * {@link Serializer} that encodes/decodes it, and the {@link PacketDirection} it's restricted to
 * (or {@link PacketDirection#BIDIRECTIONAL} for none). Captured once so {@code NetworkBootstrap}
 * never has to re-inspect the record's annotations/interfaces itself.
 */
public record PacketDescriptor<T>(int id, Class<T> type, Serializer<T> serializer, PacketDirection direction) {
}
