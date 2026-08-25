package com.dimalab.storymodengine.common.event;

import com.dimalab.storymodengine.api.event.Event;
/**
 * Posted once, by {@code EventBootstrap.init}, after every discovery pass for one mod has finished
 * ({@code ContentDiscovery}, {@code NetworkBootstrap}, {@code CapabilityBootstrap}, then this
 * package's own {@code EventListenerDiscovery}) — a checkpoint marking "this mod's engine setup is
 * complete", not a gameplay event.
 *
 * <p>Deliberately a single, generic lifecycle marker rather than one event per annotation type
 * discovered: a future engine subsystem that introduces its own annotation just adds its own
 * discovery call ahead of this post in {@code EngineBootstrap.init} and can react to (or ignore)
 * this same event — nothing about its shape is tied to today's specific annotation set
 * ({@code @AutoContent}/{@code @Packet}/{@code @Capability}/{@code @SubscribeEvent}).
 */
public record AnnotationProcessorEvent(String modId) implements Event {
}
