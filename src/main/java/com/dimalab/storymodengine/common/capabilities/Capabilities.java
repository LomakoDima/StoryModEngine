package com.dimalab.storymodengine.common.capabilities;

import com.dimalab.storymodengine.common.capabilities.registry.CapabilityDescriptor;
import com.dimalab.storymodengine.common.capabilities.registry.CapabilityRegistry;
import com.dimalab.storymodengine.common.capabilities.storage.CapabilityStorage;
import com.dimalab.storymodengine.common.capabilities.sync.CapabilitySyncPacket;
import com.dimalab.storymodengine.common.logging.EngineLog;
import com.dimalab.storymodengine.common.network.Network;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.capabilities.ICapabilityProvider;

/**
 * The entire public API for reading and synchronizing {@code @Capability} data — everything a mod
 * author writes after declaring a data class. {@code Capability<T>}, {@code LazyOptional}, {@code
 * AttachCapabilitiesEvent}, and every other Forge capability type stay behind this facade.
 *
 * <pre>{@code
 * StoryPlayerData data = Capabilities.get(player, StoryPlayerData.class);
 * data.storyPoints += 10;
 * Capabilities.markDirty(player, StoryPlayerData.class);
 * Capabilities.sync(player, StoryPlayerData.class);
 * }</pre>
 *
 * {@code owner} is typed as {@code ICapabilityProvider} rather than {@code Object} — {@code
 * Entity}, {@code BlockEntity}, and {@code Level} all satisfy it directly (verified against
 * source: all three extend Forge's own {@code CapabilityProvider<B>}), so no cast or wrapper is
 * needed at any call site regardless of owner kind.
 */
public final class Capabilities {

    private Capabilities() {
    }

    /** The owner's own instance of {@code dataType}, or {@code null} if that type was never registered or never attached here. */
    public static <T> T get(ICapabilityProvider owner, Class<T> dataType) {
        CapabilityStorage storage = storageOf(owner);
        return storage == null ? null : storage.get(dataType);
    }

    /**
     * Marks {@code dataType} changed on {@code owner}. Dirty state today is a call-site signal, not
     * an automatically-tracked flag (proxying arbitrary mutable field writes isn't something this
     * engine attempts — see {@code ARCHITECTURE.md}); the actual point of calling it is so intent
     * reads clearly at the call site even before automatic tracking exists to act on it. Persistence
     * itself needs no dirty flag at all — {@code Entity}/{@code BlockEntity} save unconditionally
     * whenever the game saves, and so does {@code CapabilityStorage} inside them.
     */
    public static void markDirty(ICapabilityProvider owner, Class<?> dataType) {
        if (get(owner, dataType) == null) {
            EngineLog.channel("Capabilities").warn(
                    "markDirty({}) — no such data attached to {}", dataType.getSimpleName(), owner);
        }
    }

    /** Same as {@link #get}/{@link #sync}, taking the field's own handle instead of repeating its data type. */
    public static <T> T get(ICapabilityProvider owner, CapabilityData<T> handle) {
        return get(owner, handle.descriptor().dataType());
    }

    public static void markDirty(ICapabilityProvider owner, CapabilityData<?> handle) {
        markDirty(owner, handle.descriptor().dataType());
    }

    public static void sync(ICapabilityProvider owner, CapabilityData<?> handle) {
        sync(owner, handle.descriptor().dataType());
    }

    /**
     * Pushes {@code owner}'s current value of {@code dataType} to the appropriate client(s) via the
     * existing {@code network} system — only ever server → client (see the class doc and {@code
     * ARCHITECTURE.md}'s security model). No-ops with a warning if {@code dataType} wasn't declared
     * {@code @Capability(sync = true)}.
     */
    public static void sync(ICapabilityProvider owner, Class<?> dataType) {
        CapabilityDescriptor<?> descriptor = CapabilityRegistry.forType(dataType);
        if (descriptor == null) {
            EngineLog.channel("Capabilities").warn("sync({}) — not a registered @Capability type", dataType.getSimpleName());
            return;
        }
        if (!descriptor.sync()) {
            EngineLog.channel("Capabilities").warn(
                    "sync({}) — not declared @Capability(sync = true)", dataType.getSimpleName());
            return;
        }
        Object value = get(owner, dataType);
        if (value == null) {
            return;
        }
        switch (descriptor.ownerKind()) {
            case ENTITY -> syncEntity(descriptor, (Entity) owner, value);
            case BLOCK_ENTITY, LEVEL -> EngineLog.channel("Capabilities").warn(
                    "sync({}) — {} owners aren't wired to a Network distribution target yet "
                            + "(extension point — see ARCHITECTURE.md)",
                    dataType.getSimpleName(), descriptor.ownerKind());
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> void syncEntity(CapabilityDescriptor<T> descriptor, Entity entity, Object value) {
        byte[] payload = descriptor.serializer().toBytes((T) value);
        CapabilitySyncPacket packet = new CapabilitySyncPacket(descriptor.id(), entity.getId(), payload);

        if (entity instanceof ServerPlayer player) {
            Network.sendToPlayer(player, packet);
        } else {
            Network.sendToTracking(entity, packet);
        }
        EngineLog.channel("Capabilities").debug("Synchronized {} → {}", descriptor.id(), entity);
    }

    private static CapabilityStorage storageOf(ICapabilityProvider owner) {
        return owner.getCapability(CapabilityLifecycle.CAPABILITY).resolve().orElse(null);
    }
}
