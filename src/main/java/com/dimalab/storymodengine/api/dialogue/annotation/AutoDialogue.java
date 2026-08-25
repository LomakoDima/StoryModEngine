package com.dimalab.storymodengine.api.dialogue.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@code static DialogueDefinition} field for automatic registration into {@code
 * dialogue.registry.DialogueRegistry} — the same {@code ModFileScanData} discovery mechanism {@code
 * @AutoContent}/{@code @AutoFlow}/{@code @Packet}/{@code @Capability} already use. Manual {@code
 * DialogueRegistry.register(...)} calls remain fully supported side by side.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface AutoDialogue {
}
