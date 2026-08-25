package com.dimalab.storymodengine.api.event;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as an event listener — the event type is inferred from the method's single
 * parameter, never declared on the annotation itself:
 *
 * <pre>{@code
 * @SubscribeEvent
 * public static void onQuestCompleted(QuestCompleted event) {
 * }
 * }</pre>
 *
 * A {@code static} method on a class discovered anywhere in the mod's own jar is registered
 * automatically ({@code EventListenerDiscovery}, wired into {@code EngineBootstrap.init}) — no
 * {@code EventBus.register(...)} call needed. An instance method requires the mod author to call
 * {@code EventBus.register(this)} (or {@code Events.register(this)}) once, since the engine has no
 * way to construct an arbitrary listener instance on its own.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface SubscribeEvent {

    /** Execution order relative to other listeners of the same (or a supertype) event. */
    EventPriority priority() default EventPriority.NORMAL;
}
