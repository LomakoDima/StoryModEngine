package com.dimalab.storymodengine.common.network.context;

import com.dimalab.storymodengine.client.network.context.ClientLevelLookup;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.util.concurrent.CompletableFuture;

/**
 * What a packet's {@code PacketHandler#handle} actually needs, in front of Forge's {@code
 * NetworkEvent.Context} — chosen deliberately over exposing that type directly so this is the one
 * seam through which future capability (a reply helper, a rate-limit token, a decoded protocol
 * version, ...) can be added without changing every existing {@code handle} method's signature.
 *
 * <p>{@link #level()} exists because {@code NetworkEvent.Context} itself has no such accessor
 * (verified against source): on the server it comes from {@link #sender()}'s own level, on the
 * client from {@link ClientLevelLookup} (see its Javadoc for why that lookup is its own class and
 * not inlined here) — both {@code null} only in genuinely exceptional states (a client packet
 * arriving before the player entity exists, or similar), which callers should treat as "drop this
 * packet", not dereference blindly.
 */
public final class PacketContext {

    private final NetworkEvent.Context forge;

    public PacketContext(NetworkEvent.Context forge) {
        this.forge = forge;
    }

    /** The sender, only ever non-null for a packet actually received on the server from a client. */
    public ServerPlayer sender() {
        return forge.getSender();
    }

    /** The receiving side's {@link Level} — the sender's own level on the server, {@code Minecraft.getInstance().level} on the client. */
    public Level level() {
        ServerPlayer player = forge.getSender();
        if (player != null) {
            return player.level();
        }
        return isClient() ? ClientLevelLookup.get() : null;
    }

    public NetworkDirection direction() {
        return forge.getDirection();
    }

    public boolean isClient() {
        return forge.getDirection().getReceptionSide().isClient();
    }

    public boolean isServer() {
        return forge.getDirection().getReceptionSide().isServer();
    }

    /** Runs {@code task} on the receiving side's main thread — the network thread otherwise, per Forge's own contract. */
    public CompletableFuture<Void> enqueue(Runnable task) {
        return forge.enqueueWork(task);
    }
}
