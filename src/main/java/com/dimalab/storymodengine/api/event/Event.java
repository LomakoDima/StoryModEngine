package com.dimalab.storymodengine.api.event;

/**
 * The base marker every engine event implements — deliberately zero methods. Cancellation, a
 * result value, networking, serialization, and Minecraft types are all optional capabilities a
 * concrete event opts into (see {@link CancellableEvent}), never forced here. A plain engine-owned
 * fact with nothing to do with Minecraft is a fully legitimate event:
 *
 * <pre>{@code
 * public record QuestCompleted(String questId) implements Event {
 * }
 * }</pre>
 */
public interface Event {
}
