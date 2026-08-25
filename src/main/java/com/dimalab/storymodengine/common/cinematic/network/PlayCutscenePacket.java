package com.dimalab.storymodengine.common.cinematic.network;

import com.dimalab.storymodengine.client.cinematic.ClientCutscenePlayer;
import com.dimalab.storymodengine.api.network.ClientboundPacket;
import com.dimalab.storymodengine.api.network.PacketHandler;
import com.dimalab.storymodengine.api.network.annotation.Packet;
import com.dimalab.storymodengine.common.network.context.PacketContext;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/**
 * "Play cutscene X, with these actor bindings" — the entire runtime network footprint this system
 * needs (see the "no per-tick sync" section of {@code ARCHITECTURE.md}). The server decides
 * whether to send this; the client, on receiving it, deterministically evaluates the *same*
 * {@code CutsceneDefinition} (already loaded identically on both sides via {@code
 * @AutoCutscene} discovery) with no further packets required to finish playback. {@code
 * actorEntityIds} carries the symbolic-name → runtime-entity-id map a definition's {@code
 * ActorBinding}s resolve through (see {@code CutsceneContext}) — resolved through the existing
 * {@code SerializerRegistry}'s generic {@code Map}/{@code String}/{@code Integer} support, nothing
 * new registered for it.
 *
 * <p>{@code startTick} is {@code 0} for a normal start; {@code CinematicManager#resumeAll} sends a
 * non-zero value when restoring a cutscene that was still playing at logout — the client simply
 * plays from tick 0 as always, then {@code ClientCutscenePlayer} immediately {@code seek}s to it
 * (the exact same, already-built playback-control machinery a player-issued {@code seek} command
 * uses), rather than this needing any new client-side concept.
 */
@Packet
public record PlayCutscenePacket(ResourceLocation cutsceneId, Map<String, Integer> actorEntityIds, int startTick)
        implements ClientboundPacket, PacketHandler {

    @Override
    public void handle(PacketContext context) {
        ClientCutscenePlayer.play(cutsceneId, actorEntityIds, startTick);
    }
}
