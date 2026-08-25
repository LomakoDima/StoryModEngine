package com.dimalab.storymodengine.common.dialogue.network;

import com.dimalab.storymodengine.common.dialogue.DialogueSystem;
import com.dimalab.storymodengine.common.logging.EngineLog;
import com.dimalab.storymodengine.api.network.PacketHandler;
import com.dimalab.storymodengine.api.network.ServerboundPacket;
import com.dimalab.storymodengine.api.network.annotation.Packet;
import com.dimalab.storymodengine.common.network.context.PacketContext;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * "I'd like to resolve the currently-parked choice/continue point to {@code transitionId}" — covers
 * both a real branch and a line's Continue (sent as the reserved id {@code "continue"}), collapsing
 * what would otherwise be two near-identical packets into one, since both ultimately do the exact
 * same thing: ask {@link DialogueSystem#selectChoice} to resolve the parked point. {@link
 * ServerboundPacket} means Forge itself already rejects a spoofed reverse-direction send (same
 * precedent {@code RequestCutsceneControlPacket} relies on); {@code DialogueSystem} additionally
 * validates that this is actually offered right now before ever touching the underlying {@code
 * Flow} — never trusts the client.
 */
@Packet
public record DialogueChoicePacket(ResourceLocation dialogueId, String transitionId) implements ServerboundPacket, PacketHandler {

    @Override
    public void handle(PacketContext context) {
        ServerPlayer sender = context.sender();
        if (sender == null) {
            EngineLog.channel("Dialogue").warn("DialogueChoicePacket received with no sender — dropping");
            return;
        }
        DialogueSystem.selectChoice(sender, dialogueId, transitionId);
    }
}
