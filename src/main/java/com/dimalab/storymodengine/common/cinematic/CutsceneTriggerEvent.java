package com.dimalab.storymodengine.common.cinematic;

import com.dimalab.storymodengine.api.event.Event;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * Posted when a {@link Trigger} fires — the only cutscene event posted from *either* side (the
 * server's own tick-counting and the client's full timeline evaluation both check the same
 * deterministic trigger ticks independently, so this reaches whichever side actually reached that
 * tick; see {@link Trigger}'s Javadoc). {@code player} is the plain {@link Player} base type
 * rather than {@code ServerPlayer}, since a client-posted instance only ever has a client player.
 */
public record CutsceneTriggerEvent(Player player, ResourceLocation cutsceneId, ResourceLocation triggerId) implements Event {
}
