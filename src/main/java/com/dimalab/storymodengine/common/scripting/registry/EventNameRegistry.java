package com.dimalab.storymodengine.common.scripting.registry;

import com.dimalab.storymodengine.api.event.Event;
import com.dimalab.storymodengine.common.event.bridge.PlayerConnectedEvent;
import com.dimalab.storymodengine.common.event.bridge.PlayerDisconnectedEvent;
import com.dimalab.storymodengine.common.quest.bridgeevent.EntityKilledEvent;
import com.dimalab.storymodengine.common.quest.event.QuestCompletedEvent;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * {@code "player.join"}/{@code "entity.kill"}/... {@code -> Class<? extends Event>} — the engine has
 * no generic namespaced-event convention (every built-in event is its own concrete record class), so
 * {@code wait event x.y} and {@code trigger { when event x.y }} both resolve through this small,
 * extensible table rather than a raw Java class name. {@code register} is public so a mod author's
 * own bootstrap can add names for its own {@link Event} types, the same extensibility spirit as
 * {@code @StoryCommand}.
 *
 * <p><b>Known MVP limitation</b>: matching is by event <em>type</em> only — {@code "quest.completed"}
 * matches every {@link QuestCompletedEvent}, not one specific quest's completion. A story with only
 * one quest never observes the difference; a story with several should prefer {@code objective quest
 * <id>} (which already waits on one specific quest via {@code Objective.quest}) over a trigger for
 * per-quest completion tracking until this registry grows argument-level filtering.
 */
public final class EventNameRegistry {

    private static final Map<String, Class<? extends Event>> TYPES = new ConcurrentHashMap<>();
    private static final Map<String, Function<Event, ServerPlayer>> EXTRACTORS = new ConcurrentHashMap<>();

    static {
        register("player.join", PlayerConnectedEvent.class, e -> ((PlayerConnectedEvent) e).player());
        register("player.leave", PlayerDisconnectedEvent.class, e -> ((PlayerDisconnectedEvent) e).player());
        register("entity.kill", EntityKilledEvent.class, e -> ((EntityKilledEvent) e).killer());
        register("quest.completed", QuestCompletedEvent.class, e -> ((QuestCompletedEvent) e).player());
    }

    private EventNameRegistry() {
    }

    public static <E extends Event> void register(String name, Class<E> type, Function<E, ServerPlayer> playerExtractor) {
        TYPES.put(name, type);
        @SuppressWarnings("unchecked")
        Function<Event, ServerPlayer> erased = (Function<Event, ServerPlayer>) (Function<?, ServerPlayer>) playerExtractor;
        EXTRACTORS.put(name, erased);
    }

    public static boolean isKnown(String name) {
        return TYPES.containsKey(name);
    }

    public static Class<? extends Event> classFor(String name) {
        return TYPES.get(name);
    }

    public static Function<Event, ServerPlayer> playerExtractorFor(String name) {
        return EXTRACTORS.get(name);
    }
}
