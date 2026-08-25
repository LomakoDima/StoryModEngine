package com.dimalab.storymodengine.common.trigger;

import com.dimalab.storymodengine.common.trigger.persistence.TriggerStateStore;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code ONCE}-eligibility bookkeeping for {@link Trigger}. Non-{@code persistent} triggers (the
 * default) never touch a capability at all — a trigger with no persistence need shouldn't pay for
 * one, per the task's own "persistence is not mandatory for every trigger" instruction — they just
 * live in a plain in-memory set for the life of the server. {@code persistent() == true} routes
 * through {@link TriggerStateStore} instead, so "already fired" survives a reconnect/restart.
 */
final class TriggerFiredTracker {

    private static final Set<ResourceLocation> MEMORY_GLOBAL = ConcurrentHashMap.newKeySet();
    private static final Map<ResourceLocation, Set<UUID>> MEMORY_PER_PLAYER = new ConcurrentHashMap<>();

    private TriggerFiredTracker() {
    }

    static boolean hasFired(Trigger trigger, ServerPlayer player) {
        if (trigger.persistent()) {
            return TriggerStateStore.hasFired(trigger, player);
        }
        return MEMORY_PER_PLAYER.getOrDefault(trigger.id(), Set.of()).contains(player.getUUID());
    }

    static void markFired(Trigger trigger, ServerPlayer player) {
        if (trigger.persistent()) {
            TriggerStateStore.markFired(trigger, player);
            return;
        }
        MEMORY_PER_PLAYER.computeIfAbsent(trigger.id(), id -> ConcurrentHashMap.newKeySet()).add(player.getUUID());
    }

    static boolean hasFiredGlobal(Trigger trigger) {
        if (trigger.persistent()) {
            return TriggerStateStore.hasFiredGlobal(trigger.id());
        }
        return MEMORY_GLOBAL.contains(trigger.id());
    }

    static void markFiredGlobal(Trigger trigger) {
        if (trigger.persistent()) {
            TriggerStateStore.markFiredGlobal(trigger.id());
            return;
        }
        MEMORY_GLOBAL.add(trigger.id());
    }
}
