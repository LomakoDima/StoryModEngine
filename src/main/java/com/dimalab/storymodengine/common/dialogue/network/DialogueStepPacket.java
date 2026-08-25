package com.dimalab.storymodengine.common.dialogue.network;

import com.dimalab.storymodengine.client.dialogue.ClientDialoguePlayer;
import com.dimalab.storymodengine.api.network.ClientboundPacket;
import com.dimalab.storymodengine.api.network.PacketHandler;
import com.dimalab.storymodengine.api.network.annotation.Packet;
import com.dimalab.storymodengine.common.network.context.PacketContext;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * "Here's what's showing now" — the entire runtime network footprint this system needs on the way
 * down, sent once when a dialogue starts and again after every advance (mirrors {@code
 * PlayCutscenePacket}: one packet, no further per-frame traffic). Carries no line/choice text —
 * the client already has the identical {@link com.dimalab.storymodengine.common.dialogue.DialogueDefinition}
 * (loaded the same way on both sides via {@code @AutoDialogue}/JSON), so this only needs to say
 * *which* entry is active and which choices passed server-side condition checks. {@code
 * availableChoiceIds} is irrelevant (empty) when the active entry is a line rather than a choice
 * group.
 */
@Packet
public record DialogueStepPacket(ResourceLocation dialogueId, String nodeId, int entryIndex, List<String> availableChoiceIds)
        implements ClientboundPacket, PacketHandler {

    @Override
    public void handle(PacketContext context) {
        ClientDialoguePlayer.onStep(this);
    }
}
