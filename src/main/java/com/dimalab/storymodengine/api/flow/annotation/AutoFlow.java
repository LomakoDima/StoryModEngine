package com.dimalab.storymodengine.api.flow.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@code static FlowDefinition} field for automatic registration — the same {@code
 * ModFileScanData} discovery mechanism {@code @AutoContent}/{@code @Packet}/{@code @Capability}/
 * {@code @SubscribeEvent} already use, applied to {@code flow.registry.FlowRegistry}. Manual
 * {@code FlowRegistry.register(...)} calls (as the required example still uses) remain fully
 * supported side by side — this is purely an additive convenience for mods with many Flow
 * definitions, not a replacement.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface AutoFlow {
}
