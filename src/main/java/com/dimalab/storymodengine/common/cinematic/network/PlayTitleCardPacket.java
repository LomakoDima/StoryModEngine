package com.dimalab.storymodengine.common.cinematic.network;

import com.dimalab.storymodengine.client.cinematic.ClientTitleCardPlayer;
import com.dimalab.storymodengine.common.cinematic.titlecard.TitleCard;
import com.dimalab.storymodengine.api.network.ClientboundPacket;
import com.dimalab.storymodengine.api.network.PacketHandler;
import com.dimalab.storymodengine.api.network.annotation.Packet;
import com.dimalab.storymodengine.common.network.context.PacketContext;

/**
 * "Play this title card" — the entire runtime network footprint the title card system needs, one
 * packet, no further traffic for the rest of playback (mirrors {@code PlayCutscenePacket} exactly).
 * Unlike {@code PlayCutscenePacket} (which only carries an id, resolved through a registry both
 * sides already loaded identically), {@link TitleCard} is embedded here directly — it's plain data
 * with nothing unserializable, so there's no lambda-bearing content a registry would need to work
 * around, and no reason to introduce one.
 */
@Packet
public record PlayTitleCardPacket(TitleCard titleCard) implements ClientboundPacket, PacketHandler {

    @Override
    public void handle(PacketContext context) {
        ClientTitleCardPlayer.play(titleCard);
    }
}
