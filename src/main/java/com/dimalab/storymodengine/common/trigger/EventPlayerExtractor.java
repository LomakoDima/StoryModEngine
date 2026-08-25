package com.dimalab.storymodengine.common.trigger;

import com.dimalab.storymodengine.api.event.Event;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.lang.reflect.Method;
import java.util.function.Function;

/**
 * Resolves the near-universal-but-not-exception-free "engine events that concern a player expose a
 * no-arg {@code player()} accessor" convention (verified across every {@code Event} in this
 * codebase: 33 of 34 player-carrying events follow it — the one exception, {@code
 * EntityKilledEvent#killer()}, is exactly why {@link Trigger.Builder#on(Class, Function)} exists as
 * an explicit escape hatch). Used only by {@link Trigger.Builder#on(Class)}, once per trigger
 * definition — never on the hot event-dispatch path.
 */
final class EventPlayerExtractor {

    private EventPlayerExtractor() {
    }

    @SuppressWarnings("unchecked")
    static <E extends Event> Function<Object, ServerPlayer> tryFind(Class<E> eventType) {
        Method accessor;
        try {
            accessor = eventType.getMethod("player");
        } catch (NoSuchMethodException e) {
            return null;
        }
        if (!Player.class.isAssignableFrom(accessor.getReturnType())) {
            return null;
        }
        return event -> {
            try {
                Object result = accessor.invoke(event);
                return result instanceof ServerPlayer serverPlayer ? serverPlayer : null;
            } catch (ReflectiveOperationException e) {
                return null;
            }
        };
    }
}
