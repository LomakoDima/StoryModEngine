package com.dimalab.storymodengine.api.network;

import com.dimalab.storymodengine.common.network.context.PacketContext;

/**
 * A {@code @Packet} record implements this directly to carry its own handling logic — "the
 * developer describes only data and behavior" means exactly this: no separate handler
 * registration call, no lambda handed to the engine elsewhere. {@code this} inside {@link
 * #handle} already has every component of the received packet.
 *
 * <pre>{@code
 * @Packet
 * public record SyncPlayerState(UUID playerId, Vec3 position, float health) implements PacketHandler {
 *     @Override
 *     public void handle(PacketContext context) {
 *         // use playerId/position/health (record accessors) and context directly
 *     }
 * }
 * }</pre>
 *
 * <p>A {@code @Packet} record that doesn't implement this is still perfectly valid — it can be
 * sent and decoded, just never acted on; {@code PacketRouter} logs that at {@code debug} rather
 * than treating it as an error, since a data-only packet another future subsystem reacts to some
 * other way is a legitimate shape, not a mistake.
 */
@FunctionalInterface
public interface PacketHandler {

    /**
     * Called on the receiving side's main thread once the packet has been decoded. Never called
     * for a packet this side sent itself.
     */
    void handle(PacketContext context);
}
