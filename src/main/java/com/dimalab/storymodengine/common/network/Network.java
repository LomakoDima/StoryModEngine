package com.dimalab.storymodengine.common.network;

import com.dimalab.storymodengine.common.logging.EngineLog;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The entire public sending API — everything a mod author writes after describing a packet.
 * {@code SimpleChannel}, {@code PacketDistributor}, and every other Forge networking type stay
 * behind this facade; nothing here is Forge-shaped. Each mod using this engine gets its own
 * channel (see {@code NetworkBootstrap}), so this class keys off the packet's own {@code Class}
 * to find the right one — populated once per discovered packet when that mod's {@code
 * EngineBootstrap.init(...)} runs, looked up here on every send.
 *
 * <pre>{@code
 * Network.sendToServer(new PingServer(...));
 * Network.sendToPlayer(player, new SyncPlayerState(...));
 * Network.sendToAll(new BroadcastAnnouncement(...));
 * Network.sendToTracking(entity, new SyncEntityState(...));
 * }</pre>
 */
public final class Network {

    private static final Map<Class<?>, SimpleChannel> CHANNELS = new ConcurrentHashMap<>();

    private Network() {
    }

    /** Called once per discovered packet by {@code NetworkBootstrap} — not part of the public API. */
    static void bind(Class<?> packetType, SimpleChannel channel) {
        CHANNELS.put(packetType, channel);
    }

    private static SimpleChannel channelFor(Object packet) {
        SimpleChannel channel = CHANNELS.get(packet.getClass());
        if (channel == null) {
            throw new IllegalArgumentException("Packet type " + packet.getClass().getName()
                    + " was never registered — is it annotated @Packet, and was "
                    + "EngineBootstrap.init(modEventBus) called for its mod?");
        }
        return channel;
    }

    /** Sends {@code packet} to the server — only meaningful from the client. */
    public static <T> void sendToServer(T packet) {
        EngineLog.channel("Network").trace("Sending {} → SERVER", packet.getClass().getSimpleName());
        channelFor(packet).sendToServer(packet);
    }

    /** Sends {@code packet} to one specific player — only meaningful from the server. */
    public static <T> void sendToPlayer(ServerPlayer player, T packet) {
        EngineLog.channel("Network").trace(
                "Sending {} → PLAYER {}", packet.getClass().getSimpleName(), player.getGameProfile().getName());
        channelFor(packet).send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    /** Sends {@code packet} to every connected player — only meaningful from the server. */
    public static <T> void sendToAll(T packet) {
        EngineLog.channel("Network").trace("Sending {} → ALL", packet.getClass().getSimpleName());
        channelFor(packet).send(PacketDistributor.ALL.noArg(), packet);
    }

    /** Sends {@code packet} to every player tracking {@code entity} (not {@code entity} itself, if it's a player). */
    public static <T> void sendToTracking(Entity entity, T packet) {
        EngineLog.channel("Network").trace(
                "Sending {} → TRACKING {}", packet.getClass().getSimpleName(), entity.getName().getString());
        channelFor(packet).send(PacketDistributor.TRACKING_ENTITY.with(() -> entity), packet);
    }

    /** Same as {@link #sendToTracking}, but also to {@code entity} itself if it's a tracked player. */
    public static <T> void sendToTrackingAndSelf(Entity entity, T packet) {
        EngineLog.channel("Network").trace(
                "Sending {} → TRACKING_AND_SELF {}", packet.getClass().getSimpleName(), entity.getName().getString());
        channelFor(packet).send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity), packet);
    }
}
