package com.dimalab.storymodengine.common.network.routing;

import com.dimalab.storymodengine.common.logging.EngineLog;
import com.dimalab.storymodengine.api.network.PacketHandler;
import com.dimalab.storymodengine.common.network.context.PacketContext;
import com.dimalab.storymodengine.common.network.registry.PacketDescriptor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * The one place every packet passes through on the receiving side — decode and dispatch — so
 * future cross-cutting behavior (rate limiting, size-based rejection beyond {@code
 * SerializerRegistry#MAX_COLLECTION_SIZE}, a permission gate applied before any handler ever
 * runs) has exactly one seam to attach to, without touching {@code @Packet} records or {@code
 * NetworkBootstrap}'s wiring.
 *
 * <p>Malformed-packet safety lives here in the one place it actually works: {@code
 * IndexedMessageCodec#tryDecode} (verified against source) wraps the decoder's result in {@code
 * Optional.map}, which — since {@code Optional.map} treats a {@code null} return as "map to empty"
 * — means a decoder that catches its own exception and returns {@code null} makes Forge silently
 * skip calling the message consumer at all, no crash, no disconnect, nothing thrown up into Netty.
 * {@link #decode} relies on exactly that rather than fighting it. A handler-side exception is
 * caught the same defensive way, so one broken {@code PacketHandler#handle} can't take the
 * network thread (or the main thread, for {@code consumerMainThread}-dispatched packets) down
 * with it.
 */
public final class PacketRouter {

    private PacketRouter() {
    }

    /** Decodes {@code buf} with {@code descriptor}'s serializer, or logs and returns {@code null} on failure. */
    public static <T> T decode(String modId, PacketDescriptor<T> descriptor, FriendlyByteBuf buf) {
        try {
            return descriptor.serializer().read(buf);
        } catch (Exception e) {
            EngineLog.channel("Network").warn(
                    "[{}] Malformed {} packet, dropping: {}", modId, descriptor.type().getSimpleName(), e.toString());
            return null;
        }
    }

    /** Dispatches an already-decoded packet to its own {@link PacketHandler}, if it has one. */
    public static <T> void route(String modId, PacketDescriptor<T> descriptor, T packet,
                                  Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context forgeContext = contextSupplier.get();
        PacketContext context = new PacketContext(forgeContext);
        String side = context.isServer() ? "CLIENT" : "SERVER";

        try {
            if (packet instanceof PacketHandler handler) {
                EngineLog.channel("Network").trace(
                        "[{}] Received {} ← {}", modId, descriptor.type().getSimpleName(), side);
                handler.handle(context);
            } else {
                EngineLog.channel("Network").debug(
                        "[{}] {} has no PacketHandler, ignoring", modId, descriptor.type().getSimpleName());
            }
        } catch (Exception e) {
            EngineLog.channel("Network").warn(
                    "[{}] {} handler threw, ignoring: {}", modId, descriptor.type().getSimpleName(), e.toString());
        } finally {
            forgeContext.setPacketHandled(true);
        }
    }
}
