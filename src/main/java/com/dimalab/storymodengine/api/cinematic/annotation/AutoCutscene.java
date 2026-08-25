package com.dimalab.storymodengine.api.cinematic.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@code static CutsceneDefinition} field for automatic registration — the same {@code
 * ModFileScanData} discovery every other {@code @Auto*}/{@code @Capability}/{@code @Packet}
 * annotation in this engine uses, applied to {@code cinematic.registry.CutsceneRegistry}. Manual
 * {@code CutsceneRegistry.register(...)} remains fully supported alongside it.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface AutoCutscene {
}
