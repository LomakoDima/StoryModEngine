package com.dimalab.storymodengine.common.cinematic.network;

import com.dimalab.storymodengine.common.cinematic.CinematicManager;
import com.dimalab.storymodengine.common.logging.EngineLog;
import com.dimalab.storymodengine.api.network.PacketHandler;
import com.dimalab.storymodengine.api.network.ServerboundPacket;
import com.dimalab.storymodengine.api.network.annotation.Packet;
import com.dimalab.storymodengine.common.network.context.PacketContext;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * "I'd like to pause/resume/seek/speed-up/skip my cutscene" — the client asks, the server decides.
 * One consolidated packet for all five actions rather than five near-identical ones (only {@code
 * tickArg}/{@code speedArg} are ever relevant, per {@link CutsceneControlAction}). {@link
 * ServerboundPacket} means Forge itself already rejects a spoofed reverse-direction send (verified
 * against the same {@code NetworkHooks.validatePacketDirection} precedent {@code PingServer} relies
 * on); {@code CinematicManager} additionally validates the request semantically (does the sender
 * actually have *this* cutscene active, is a forward jump allowed under its {@code SkipPolicy}) —
 * real trust boundary enforcement, not just a direction check — and always replies with {@link
 * SyncCutscenePlaybackPacket} reflecting what was *actually* applied, whether that matches the
 * request or not.
 */
@Packet
public record RequestCutsceneControlPacket(ResourceLocation cutsceneId, CutsceneControlAction action, int tickArg, float speedArg)
        implements ServerboundPacket, PacketHandler {

    @Override
    public void handle(PacketContext context) {
        ServerPlayer sender = context.sender();
        if (sender == null) {
            EngineLog.channel("Cinematic").warn("RequestCutsceneControlPacket received with no sender — dropping");
            return;
        }
        CinematicManager.handleControlRequest(sender, cutsceneId, action, tickArg, speedArg);
    }
}
