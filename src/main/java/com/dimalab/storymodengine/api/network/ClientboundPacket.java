package com.dimalab.storymodengine.api.network;

/**
 * Optional marker a {@code @Packet} record implements to declare it only ever travels server →
 * client. See {@link ServerboundPacket} — same mechanism, opposite direction.
 */
public interface ClientboundPacket {
}
