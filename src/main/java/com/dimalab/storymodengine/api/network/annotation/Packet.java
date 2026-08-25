package com.dimalab.storymodengine.api.network.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@code record} as network-transmittable content. {@code PacketDiscovery} finds every
 * such type in the owning mod's jar via Forge's own annotation scan data — the same mechanism
 * {@code AutoContent} already uses for fields — and registers it with a {@code SimpleChannel}
 * automatically: id, serialization, and direction are all inferred, never declared here.
 *
 * <p>The annotated type must be a {@code record} — that constraint is what makes automatic
 * serialization possible at all (a record's components are its entire, enumerable state, readable
 * via {@code Class#getRecordComponents()}) and is also exactly what "the developer describes only
 * data" means in practice. A record's own methods carry its behavior: implement
 * {@code com.dimalab.storymodengine.api.network.PacketHandler} directly on the record to handle it
 * where it's received, or {@code ServerboundPacket}/{@code ClientboundPacket} to restrict which
 * direction it may legally travel — neither is required.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Packet {
}
