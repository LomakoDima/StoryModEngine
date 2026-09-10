package com.dimalab.storymodengine.api.capabilities.annotation;

import com.dimalab.storymodengine.api.capabilities.SyncAudience;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@code static CapabilityData<T>} field as persistent, attachable data. {@code
 * CapabilityDiscovery} finds every such field in the owning mod's jar via Forge's own annotation
 * scan data — the same mechanism {@code @AutoContent} and {@code @Packet} already use — and the
 * engine handles registering a Forge capability, attaching it to every instance of the right owner
 * kind, NBT persistence, and (if {@link #sync}) network synchronization, all without the mod
 * author touching {@code Capability}, {@code AttachCapabilitiesEvent}, or {@code
 * ICapabilitySerializable} directly.
 *
 * <p>Not named {@code @CapabilityData} — that name is used by the handle class this annotation
 * targets ({@code CapabilityData<T>}), and a single file needs to reference both under distinct
 * simple names.
 *
 * <p>The owner kind (which of {@code Entity}/{@code BlockEntity}/{@code Level} this data attaches
 * to) is not a parameter here — it's inferred from which of {@code EntityCapability}/{@code
 * BlockEntityCapability}/{@code LevelCapability} the data type {@code T} itself implements, the
 * same "optional marker interface instead of an annotation parameter" convention {@code
 * ServerboundPacket}/{@code ClientboundPacket} already established in {@code network}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Capability {

    /**
     * Whether this data can be pushed to clients via {@code Capabilities#sync}. {@code false} by
     * default: most persistent data is server-only bookkeeping that never needs a client copy.
     */
    boolean sync() default false;

    /**
     * Who {@code sync} reaches, when {@code sync() == true}. Defaults to {@link SyncAudience#AUTO} —
     * today's exact owner-type-inferred behavior — so this parameter is opt-in and every existing
     * {@code @Capability(sync = true)} declaration keeps its current behavior unchanged.
     */
    SyncAudience audience() default SyncAudience.AUTO;
}
