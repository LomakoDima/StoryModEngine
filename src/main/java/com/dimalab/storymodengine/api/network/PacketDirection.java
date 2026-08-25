package com.dimalab.storymodengine.api.network;

import net.minecraftforge.network.NetworkDirection;

import java.util.Optional;

/**
 * The three directions a {@code @Packet} record can travel — {@link #of} is how {@code
 * PacketDiscovery} derives one from whichever of {@link ServerboundPacket}/{@link
 * ClientboundPacket} (if either) a discovered record implements. {@link #BIDIRECTIONAL} is the
 * default for a record implementing neither, and maps to no Forge-level direction check at all
 * ({@link #forge()} returns empty) — see those two interfaces' Javadoc for what that trades away.
 */
public enum PacketDirection {
    CLIENT_TO_SERVER(NetworkDirection.PLAY_TO_SERVER),
    SERVER_TO_CLIENT(NetworkDirection.PLAY_TO_CLIENT),
    BIDIRECTIONAL(null);

    private final NetworkDirection forge;

    PacketDirection(NetworkDirection forge) {
        this.forge = forge;
    }

    /** The concrete {@code NetworkDirection} to enforce, or empty for no enforcement ({@link #BIDIRECTIONAL}). */
    public Optional<NetworkDirection> forge() {
        return Optional.ofNullable(forge);
    }

    /**
     * Derives a direction from a discovered packet type's declared markers. Returns {@code null}
     * (not {@link #BIDIRECTIONAL}) if the type implements both {@link ServerboundPacket} and
     * {@link ClientboundPacket} — a contradiction {@code PacketDiscovery} treats as a discovery
     * error, not a valid direction.
     */
    public static PacketDirection of(Class<?> type) {
        boolean serverbound = ServerboundPacket.class.isAssignableFrom(type);
        boolean clientbound = ClientboundPacket.class.isAssignableFrom(type);
        if (serverbound && clientbound) {
            return null;
        }
        if (serverbound) {
            return CLIENT_TO_SERVER;
        }
        if (clientbound) {
            return SERVER_TO_CLIENT;
        }
        return BIDIRECTIONAL;
    }
}
