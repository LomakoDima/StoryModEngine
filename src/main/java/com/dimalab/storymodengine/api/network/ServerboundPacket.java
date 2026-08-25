package com.dimalab.storymodengine.api.network;

/**
 * Optional marker a {@code @Packet} record implements to declare it only ever travels client →
 * server. Purely opt-in — the "other way" {@code Packet}'s own contract promises for determining
 * direction without forcing a parameter on the annotation. Once declared, Forge itself rejects
 * (disconnects) a connection that tries to send this packet the other way (verified against
 * {@code NetworkHooks.validatePacketDirection} source) — real spoofing protection, not just
 * documentation.
 *
 * <p>A record implementing neither this nor {@link ClientboundPacket} is treated as bidirectional:
 * Forge performs no direction check for it, and {@link PacketHandler#handle} must itself branch on
 * {@link PacketContext#isClient()}/{@link PacketContext#isServer()} if its behavior differs by
 * side. A record implementing <em>both</em> is a configuration error {@code PacketDiscovery} skips
 * with a warning, the same way {@code ContentDiscovery} skips a malformed {@code @AutoContent}
 * field rather than crashing mod loading.
 */
public interface ServerboundPacket {
}
