package com.dimalab.storymodengine.api.trigger.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@code public static final Trigger} field for automatic discovery — the exact same shape
 * as {@code @AutoQuest}/{@code @AutoFlow}/{@code @AutoDialogue}: no manual {@code
 * TriggerRegistry.register(...)} call anywhere in mod-author code.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface AutoTrigger {
}
