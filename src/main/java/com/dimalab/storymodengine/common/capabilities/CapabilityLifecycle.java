package com.dimalab.storymodengine.common.capabilities;

import com.dimalab.storymodengine.api.capabilities.OwnerKind;
import com.dimalab.storymodengine.common.capabilities.registry.CapabilityRegistry;
import com.dimalab.storymodengine.common.capabilities.storage.CapabilityStorage;
import com.dimalab.storymodengine.common.logging.EngineLog;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The Forge-bus wiring behind every {@code @Capability} declaration — the engine's own answer to
 * "{@code CapabilityManager}" from the task's suggested file list, renamed to avoid colliding with
 * Forge's own {@code net.minecraftforge.common.capabilities.CapabilityManager}, which this class
 * calls directly.
 *
 * <p>{@link #CAPABILITY} is the <em>one</em> real Forge {@code Capability<CapabilityStorage>} this
 * whole engine ever creates. That's not an arbitrary simplification: {@code CapabilityToken<T>}'s
 * generic-capturing ASM transformer only works for a fixed {@code T} at a fixed anonymous-class
 * call site (verified against source), so a reflective, per-{@code @Capability}-data-class
 * {@code Capability<T>} is not something the JVM's type erasure allows building at runtime. Every
 * mod author's data class instead lives *inside* the single shared {@link CapabilityStorage}
 * attached to each owner — see its Javadoc.
 *
 * <p>Because that token is JVM-wide, so is its attachment wiring: {@link #registerOnce} is
 * idempotent (an {@link AtomicBoolean} guard), safe to call once per mod using this engine without
 * double-registering the same Forge-bus listeners or re-firing {@link RegisterCapabilitiesEvent}
 * for {@link CapabilityStorage} (which throws if done twice — verified against {@code
 * CapabilityManager} source). {@link CapabilityRegistry} being global (not per-mod) is what makes
 * this correct rather than merely convenient: every mod's descriptors end up in the same registry,
 * so the one shared listener set serves all of them.
 */
public final class CapabilityLifecycle {

    /** The single Forge capability key every {@link CapabilityStorage} is attached and looked up under. */
    public static final Capability<CapabilityStorage> CAPABILITY = CapabilityManager.get(new CapabilityToken<>() {
    });

    private static final ResourceLocation KEY = ResourceLocation.fromNamespaceAndPath("storymodengine", "data");
    private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);

    private CapabilityLifecycle() {
    }

    /** Registers the attachment/clone listeners exactly once per JVM. Safe to call from every mod using this engine. */
    public static void registerOnce(IEventBus modEventBus) {
        if (REGISTERED.compareAndSet(false, true)) {
            modEventBus.addListener((RegisterCapabilitiesEvent event) -> event.register(CapabilityStorage.class));
            MinecraftForge.EVENT_BUS.register(CapabilityLifecycle.class);
        }
    }

    @SubscribeEvent
    public static void attachEntity(AttachCapabilitiesEvent<Entity> event) {
        attach(event, OwnerKind.ENTITY);
    }

    @SubscribeEvent
    public static void attachBlockEntity(AttachCapabilitiesEvent<BlockEntity> event) {
        attach(event, OwnerKind.BLOCK_ENTITY);
    }

    @SubscribeEvent
    public static void attachLevel(AttachCapabilitiesEvent<Level> event) {
        attach(event, OwnerKind.LEVEL);
    }

    private static void attach(AttachCapabilitiesEvent<?> event, OwnerKind kind) {
        if (CapabilityRegistry.forOwner(kind).isEmpty()) {
            return;
        }
        CapabilityStorage storage = new CapabilityStorage(kind);
        event.addCapability(KEY, storage);
        event.addListener(storage::invalidate);
        EngineLog.channel("Capabilities").trace(
                "Attached data ({}) → {}", kind, event.getObject().getClass().getSimpleName());
    }

    /**
     * Carries {@code ENTITY}-kind data across a replaced {@code Player} instance — death/respawn or a
     * dimension change both destroy and recreate the entity (verified against {@code PlayerEvent.Clone}'s
     * own Javadoc). Without this, persistent player data attached before the swap would simply be
     * gone: the new instance starts with fresh, default-constructed data. {@code reviveCaps()}/{@code
     * invalidateCaps()} bracket the copy exactly as {@code CapabilityProvider}'s own Javadoc
     * describes for reading from an already-invalidated (removed) provider.
     */
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (CapabilityRegistry.forOwner(OwnerKind.ENTITY).isEmpty()) {
            return;
        }
        Player original = event.getOriginal();
        Player fresh = event.getEntity();

        original.reviveCaps();
        try {
            original.getCapability(CAPABILITY).ifPresent(oldStorage ->
                    fresh.getCapability(CAPABILITY).ifPresent(newStorage -> {
                        newStorage.copyFrom(oldStorage);
                        EngineLog.channel("Capabilities").debug(
                                "Carried player data across {} for {}",
                                event.isWasDeath() ? "death/respawn" : "dimension change",
                                fresh.getGameProfile().getName());
                    }));
        } finally {
            original.invalidateCaps();
        }
    }
}
