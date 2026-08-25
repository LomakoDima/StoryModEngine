package com.dimalab.storymodengine.api.quest.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@code static QuestDefinition} field for automatic discovery — byte-for-byte the same
 * shape as {@code dialogue.annotation.AutoDialogue}/{@code flow.annotation.AutoFlow}, scanned by
 * {@code quest.discovery.QuestDiscovery}. No manual {@code QuestRegistry.register(...)} call needed.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface AutoQuest {
}
