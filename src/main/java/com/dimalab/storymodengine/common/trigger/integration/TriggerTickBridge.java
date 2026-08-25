package com.dimalab.storymodengine.common.trigger.integration;

import com.dimalab.storymodengine.common.trigger.Trigger;
import com.dimalab.storymodengine.common.trigger.TriggerKind;
import com.dimalab.storymodengine.common.trigger.TriggerRegistry;
import com.dimalab.storymodengine.common.trigger.TriggerSystem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The one place {@code trigger} touches a genuine Minecraft tick — mirrors {@code
 * flow.integration.FlowTickBridge}/{@code cinematic.integration.CinematicTickBridge} exactly, down
 * to the {@code Phase.END} guard and the {@code AtomicBoolean}-guarded {@link #registerOnce}.
 *
 * <p>Polls only {@code LOCATION}/{@code TIME} triggers — {@code EVENT} triggers need no polling at
 * all, they're armed once via {@code TriggerSystem#arm}. This is the same "poll only what's actually
 * armed, never broadcast a global per-tick signal" discipline {@code quest.objective
 * .LocationObjective}/{@code FindEntityObjective} already established (there is still no {@code
 * PlayerMovedEvent} anywhere in this engine, deliberately) — the difference here is one shared poll
 * across every registered location/time trigger instead of one poll per active quest objective,
 * since a trigger has no per-instance lifecycle to hang a {@code Flow.lazy}/{@code wait} recursion
 * off of the way a quest objective does.
 *
 * <p>Runs every {@link Trigger#TIME_CHECK_WINDOW_TICKS} ticks (20, matching {@code
 * LocationObjective}'s own polling interval) — there is no scheduler here, just a plain counter, the
 * same "no existing throttle idiom to reuse, but nothing duplicated by adding one" call the rest of
 * this engine already makes for one-off interval checks.
 *
 * <p><b>In-memory only, deliberately</b>: the per-(trigger, player) "was already inside" / per-trigger
 * "was already in the matching time window" edge-detection state below is <em>not</em> persisted —
 * it only decides whether a check on this specific tick counts as a fresh ENTER/EXIT/time-window
 * crossing, not whether the trigger is allowed to fire at all (that's {@code TriggerFiredTracker}'s
 * job, which *is* persisted when {@code persistent()} is set). A restart mid-window can cause at most
 * one spurious extra edge-detection on the very next poll — for a {@code REPEAT} trigger that's a
 * rare, harmless extra firing; for {@code ONCE} it's fully absorbed by the real (persisted)
 * eligibility check in {@code TriggerFiredTracker}.
 */
public final class TriggerTickBridge {

    private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);

    private static int counter = 0;

    private static final Map<ResourceLocation, Set<UUID>> insideNow = new ConcurrentHashMap<>();
    private static final Set<ResourceLocation> timeMatchingNow = ConcurrentHashMap.newKeySet();

    private TriggerTickBridge() {
    }

    public static void registerOnce() {
        if (REGISTERED.compareAndSet(false, true)) {
            MinecraftForge.EVENT_BUS.register(TriggerTickBridge.class);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (++counter % Trigger.TIME_CHECK_WINDOW_TICKS != 0) {
            return;
        }

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        long dayTime = server.overworld().getDayTime() % 24000;

        for (Trigger trigger : TriggerRegistry.all().values()) {
            if (!trigger.enabled()) {
                continue;
            }
            if (trigger.kind() == TriggerKind.LOCATION) {
                checkLocation(trigger, players);
            } else if (trigger.kind() == TriggerKind.TIME) {
                checkTime(trigger, (int) dayTime);
            }
        }
    }

    private static void checkLocation(Trigger trigger, List<ServerPlayer> players) {
        Set<UUID> wasInside = insideNow.computeIfAbsent(trigger.id(), id -> ConcurrentHashMap.newKeySet());
        for (ServerPlayer player : players) {
            boolean nowInside = trigger.containsPosition(player.position());
            boolean wasInsideBefore = wasInside.contains(player.getUUID());
            if (nowInside && !wasInsideBefore) {
                wasInside.add(player.getUUID());
                if (!trigger.whenExited()) {
                    TriggerSystem.fireForPlayer(trigger, player);
                }
            } else if (!nowInside && wasInsideBefore) {
                wasInside.remove(player.getUUID());
                if (trigger.whenExited()) {
                    TriggerSystem.fireForPlayer(trigger, player);
                }
            }
        }
    }

    private static void checkTime(Trigger trigger, int dayTime) {
        boolean matches = trigger.matchesTime(dayTime);
        boolean wasMatching = timeMatchingNow.contains(trigger.id());
        if (matches && !wasMatching) {
            timeMatchingNow.add(trigger.id());
            TriggerSystem.fireGlobal(trigger);
        } else if (!matches && wasMatching) {
            timeMatchingNow.remove(trigger.id());
        }
    }
}
