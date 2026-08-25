package com.dimalab.storymodengine.common.event.bridge;

import com.dimalab.storymodengine.common.event.Events;
import com.dimalab.storymodengine.common.logging.EngineLog;
import com.dimalab.storymodengine.common.quest.bridgeevent.BlockInteractionEvent;
import com.dimalab.storymodengine.common.quest.bridgeevent.EntityInteractionEvent;
import com.dimalab.storymodengine.common.quest.bridgeevent.EntityKilledEvent;
import com.dimalab.storymodengine.common.quest.bridgeevent.ItemCollectedEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The one place the engine-level {@code event} package touches real Forge event types — everything
 * else in this package stays Forge-independent (see {@code EventBus}'s Javadoc). Translates real
 * Forge player-connection events into their engine equivalents and posts them through {@link
 * Events}, so a mod author reacting to a player joining/leaving never needs
 * {@code MinecraftForge.EVENT_BUS.register(...)} or a Forge event type — only
 * {@code @SubscribeEvent void onJoin(PlayerConnectedEvent e)}.
 *
 * <p>Registers itself on {@code MinecraftForge.EVENT_BUS} idempotently ({@link #registerOnce}),
 * the same {@link AtomicBoolean}-guarded pattern {@code CapabilityLifecycle.registerOnce} already
 * uses — not {@code @Mod.EventBusSubscriber}, which needs a compile-time modid tied to one specific
 * mod and doesn't fit engine-level infrastructure meant to serve every mod using this engine.
 *
 * <p>Extended (not duplicated) for the {@code quest} package's event-driven objectives — {@link
 * #onLivingDeath}/{@link #onItemPickup}/{@link #onEntityInteract}/{@link #onRightClickBlock} below.
 * Every one of them is guarded on the actor being a {@code ServerPlayer} (never fires for a
 * client-side echo of the same Forge event, or for a non-player entity), matching the exact guard
 * {@link #onLoggedIn}/{@link #onLoggedOut} already use.
 */
public final class MinecraftEventBridge {

    private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);

    private MinecraftEventBridge() {
    }

    /** Registers the Forge-side listeners exactly once per JVM. Safe to call from every mod using this engine. */
    public static void registerOnce() {
        if (REGISTERED.compareAndSet(false, true)) {
            MinecraftForge.EVENT_BUS.register(MinecraftEventBridge.class);
        }
    }

    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            EngineLog.channel("Events").trace("Bridging PlayerLoggedInEvent → PlayerConnectedEvent for {}", player.getGameProfile().getName());
            Events.post(new PlayerConnectedEvent(player));
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            EngineLog.channel("Events").trace("Bridging PlayerLoggedOutEvent → PlayerDisconnectedEvent for {}", player.getGameProfile().getName());
            Events.post(new PlayerDisconnectedEvent(player));
        }
    }

    /** Feeds {@code quest.Objective.kill(...)} — only when the kill credit ({@code DamageSource#getEntity()}) is a real player, never environmental/mob-on-mob deaths. */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer killer) {
            ResourceLocation entityTypeId = ForgeRegistries.ENTITY_TYPES.getKey(event.getEntity().getType());
            if (entityTypeId != null) {
                Events.post(new EntityKilledEvent(killer, entityTypeId));
            }
        }
    }

    /** Feeds {@code quest.Objective.collect(...)} — ground pickups only, see this class's own Javadoc for the documented scope limit. */
    @SubscribeEvent
    public static void onItemPickup(EntityItemPickupEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ItemStack stack = event.getItem().getItem();
            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (itemId != null) {
                Events.post(new ItemCollectedEvent(player, itemId, stack.getCount()));
            }
        }
    }

    /** Feeds {@code quest.Objective.talkTo(...)} — a plain interact signal, deliberately not tied to {@code dialogue} (see {@code Objective.dialogue} for that). */
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Entity target = event.getTarget();
            ResourceLocation entityTypeId = ForgeRegistries.ENTITY_TYPES.getKey(target.getType());
            if (entityTypeId != null) {
                Events.post(new EntityInteractionEvent(player, entityTypeId));
            }
        }
    }

    /** Feeds {@code quest.Objective.interact(...)}. */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(event.getLevel().getBlockState(event.getPos()).getBlock());
            if (blockId != null) {
                Events.post(new BlockInteractionEvent(player, event.getPos(), blockId));
            }
        }
    }
}
