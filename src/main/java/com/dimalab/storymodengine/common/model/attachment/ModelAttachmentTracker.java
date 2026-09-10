package com.dimalab.storymodengine.common.model.attachment;

import com.dimalab.storymodengine.common.StoryModEngine;
import com.dimalab.storymodengine.common.capabilities.Capabilities;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Closes a real gap in {@code Capabilities.sync}: it only pushes to players <em>currently</em>
 * tracking the entity ({@code Network.sendToTracking}), so a player who starts tracking later —
 * walks into range, or wasn't even online when {@code /sme model attach} ran — never receives the
 * attachment on their own. NBT persistence has no such gap (Forge's own {@code Entity} capability
 * patch saves/loads it unconditionally); this is sync-only.
 *
 * <p>Re-syncing on every {@code StartTracking} re-broadcasts to every other already-tracking player
 * too (see {@code Capabilities.sync}'s own {@code Network.sendToTracking} call) — redundant for them,
 * but this fires only when a new player enters range, not every tick, so the extra traffic is not
 * worth avoiding with more plumbing.
 *
 * <p><b>A player attached to themselves needs a second, separate hook.</b> A player does not track
 * their own entity the way another client tracks it — {@code StartTracking} never fires for "you
 * see yourself" — confirmed by a real relog losing the attachment visually while the same fix
 * correctly covered every other entity (a zombie, say) on that identical relog. The server-side NBT
 * round-trip was never the problem — only the fresh client's own copy of the sync packet was
 * missing. {@code Capabilities.syncEntity} already special-cases a {@code ServerPlayer} owner to
 * {@code Network.sendToPlayer} (send to that exact player, not "whoever tracks them"), so the fix is
 * simply to call that same {@code sync} once more, on login, targeting the joining player's own
 * entity.
 *
 * <p><b>Death/respawn and dimension change need the exact same second sync, for the exact same
 * reason — but NOT from {@code PlayerEvent.Clone}.</b> {@code CapabilityLifecycle.onPlayerClone}
 * already copies the whole {@code CapabilityStorage} — including {@link ModelAttachment} — from the
 * dying/departing {@code Player} instance to the fresh respawned/arrived one. But {@code Clone} fires
 * far too early to sync from directly: confirmed by reading the real decompiled source
 * (`PlayerList.respawn`), it fires from inside {@code Entity.restoreFrom}, called <em>before</em> the
 * new player's id is even reassigned to match the old one ({@code serverplayer.setId(...)} runs right
 * after) and long before {@code ClientboundRespawnPacket} is sent — the client doesn't know this
 * entity exists yet. {@code CapabilitySyncPacket}'s own handler resolves its target via {@code
 * level.getEntity(targetEntityId)} and silently drops the packet (a debug log, nothing louder) when
 * that lookup misses — exactly why an earlier attempt at this fix (syncing from {@code Clone} itself,
 * even at {@code LOWEST} priority — priority only orders <em>other listeners of the same event</em>,
 * it can't make the event itself fire later) produced no visible error and no working fix.
 *
 * <p>Fixed by moving to the two events Forge fires once the client is actually caught up — confirmed
 * by the same source read to fire only after {@code ClientboundRespawnPacket} (and, for respawn,
 * after {@code setId} too): {@code PlayerRespawnEvent} (death, or conquering the End) and {@code
 * PlayerChangedDimensionEvent} (a portal). Both cases {@code CapabilityLifecycle.onPlayerClone}
 * already handles with one {@code PlayerEvent.Clone} listener; these need two separate ones since
 * vanilla fires them from two different methods (`PlayerList.respawn`/`ServerPlayer.changeDimension`)
 * with no single later event common to both.
 */
@Mod.EventBusSubscriber(modid = StoryModEngine.MODID)
public final class ModelAttachmentTracker {

    private ModelAttachmentTracker() {
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        sync(event.getTarget());
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        sync(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        sync(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        sync(event.getEntity());
    }

    private static void sync(Entity target) {
        ModelAttachment attachment = Capabilities.get(target, ModelAttachCapabilities.MODEL_ATTACHMENT);
        if (attachment != null && !attachment.modelName.isEmpty()) {
            ModelAttachCapabilities.MODEL_ATTACHMENT.sync(target);
        }
    }
}
