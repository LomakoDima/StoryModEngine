package com.dimalab.storymodengine.common.capabilities.sync;

import com.dimalab.storymodengine.common.capabilities.Capabilities;
import com.dimalab.storymodengine.api.capabilities.OwnerKind;
import com.dimalab.storymodengine.common.capabilities.registry.CapabilityDescriptor;
import com.dimalab.storymodengine.common.capabilities.registry.CapabilityRegistry;
import com.dimalab.storymodengine.common.logging.EngineLog;
import com.dimalab.storymodengine.api.network.PacketHandler;
import com.dimalab.storymodengine.api.network.ServerboundPacket;
import com.dimalab.storymodengine.api.network.annotation.Packet;
import com.dimalab.storymodengine.common.network.context.PacketContext;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * The <em>only</em> client → server packet this system defines — "please send me the current
 * authoritative value of this data type again," never a value to write. This is what "client →
 * server synchronization must be explicit and validated" (the task's own security requirement)
 * means concretely here: rather than exposing a generic "client writes capability data" endpoint
 * that would need ad-hoc trust decisions on every field of every future data class, that endpoint
 * simply doesn't exist. A game feature that genuinely needs the client to submit a value (a GUI
 * selection, for instance) is its own specifically-validated {@code @Packet}, not a capability-
 * system primitive.
 *
 * <p>Rejects (with a log line, no crash) requests for a data type that isn't {@code sync = true},
 * isn't {@code Entity}-owned, or doesn't exist at all — a client can request only what the server
 * would have sent it anyway.
 */
@Packet
public record CapabilityResyncRequest(ResourceLocation dataId) implements ServerboundPacket, PacketHandler {

    @Override
    public void handle(PacketContext context) {
        ServerPlayer sender = context.sender();
        if (sender == null) {
            return;
        }

        CapabilityDescriptor<?> descriptor = CapabilityRegistry.byId(dataId);
        if (descriptor == null || !descriptor.sync() || descriptor.ownerKind() != OwnerKind.ENTITY) {
            EngineLog.channel("Capabilities").warn(
                    "Rejected resync request for {} from {} — not a syncable Entity capability",
                    dataId, sender.getGameProfile().getName());
            return;
        }

        Capabilities.sync(sender, descriptor.dataType());
    }
}
