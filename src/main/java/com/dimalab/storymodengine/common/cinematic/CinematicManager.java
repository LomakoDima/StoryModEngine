package com.dimalab.storymodengine.common.cinematic;

import com.dimalab.storymodengine.api.cinematic.SkipPolicy;
import com.dimalab.storymodengine.common.StoryModEngine;
import com.dimalab.storymodengine.common.capabilities.Capabilities;
import com.dimalab.storymodengine.common.cinematic.network.CutsceneControlAction;
import com.dimalab.storymodengine.common.cinematic.network.PlayCutscenePacket;
import com.dimalab.storymodengine.common.cinematic.network.PlayTitleCardPacket;
import com.dimalab.storymodengine.common.cinematic.network.StopCutscenePacket;
import com.dimalab.storymodengine.common.cinematic.network.StopTitleCardPacket;
import com.dimalab.storymodengine.common.cinematic.network.SyncCutscenePlaybackPacket;
import com.dimalab.storymodengine.common.cinematic.persistence.CutscenePlaybackData;
import com.dimalab.storymodengine.common.cinematic.persistence.ModCinematicCapabilities;
import com.dimalab.storymodengine.common.cinematic.persistence.PersistedCutscene;
import com.dimalab.storymodengine.common.cinematic.registry.CutsceneRegistry;
import com.dimalab.storymodengine.common.cinematic.state.ActorState;
import com.dimalab.storymodengine.common.cinematic.titlecard.TitleCard;
import com.dimalab.storymodengine.common.cinematic.track.ActionTrack;
import com.dimalab.storymodengine.common.cinematic.track.ActorTrack;
import com.dimalab.storymodengine.common.cinematic.track.EventTrack;
import com.dimalab.storymodengine.common.event.Events;
import com.dimalab.storymodengine.common.flow.Flow;
import com.dimalab.storymodengine.common.flow.FlowManager;
import com.dimalab.storymodengine.common.flow.registry.FlowRegistry;
import com.dimalab.storymodengine.common.logging.EngineLog;
import com.dimalab.storymodengine.api.math.Angles;
import com.dimalab.storymodengine.common.network.Network;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The server-side authority over which cutscene is playing for which player — deliberately the
 * only server-side piece this system has (see {@code ARCHITECTURE.md}'s client/server split): it
 * never evaluates a {@link com.dimalab.storymodengine.common.cinematic.track.Track}, never touches a
 * camera, and holds nothing but "who's watching what, since when." Its jobs are deciding whether a
 * cutscene may start (the "one active per player" policy below), independently detecting
 * completion by counting its own ticks against {@link Timeline#durationTicks()} — the same
 * deterministic number the client counts against locally, so no acknowledgement packet is ever
 * needed back from the client — and firing this timeline's server-authoritative one-shot cues
 * ({@link Trigger}, {@link EventTrack}, {@link ActionTrack}) at their exact tick.
 *
 * <p>{@link #tick()} guards each player's own cutscene independently: a cue/action/trigger that
 * throws is logged and that one player's cutscene is dropped, rather than aborting the whole
 * server-tick loop and silently skipping every *other* player's active cutscene that tick (a real
 * gap this class had before playback control/event/action tracks existed — see
 * {@code ARCHITECTURE.md}'s "robustness" section).
 *
 * <p>{@link #playTitleCard}/{@link #cancelTitleCard} extend this same authority to {@link
 * com.dimalab.storymodengine.common.cinematic.titlecard.TitleCard}s — tracked in their own map, on the
 * same tick source, with the same fault-isolated {@link #tick()} treatment, but never sharing a
 * "slot" with a cutscene: the two are independent presentation primitives a mod author may
 * sequence however it likes (see {@code ARCHITECTURE.md}).
 */
public final class CinematicManager {

    private static final Map<UUID, ActiveCutscene> ACTIVE = new ConcurrentHashMap<>();
    private static final Map<UUID, ActiveTitleCard> ACTIVE_TITLE_CARDS = new ConcurrentHashMap<>();

    private CinematicManager() {
    }

    /**
     * Starts {@code definition} for {@code player}, with {@code actorEntityIds} as the symbolic →
     * runtime entity-id bindings its {@code ActorTrack}s resolve through. If one is already active
     * for this player, it's cancelled first (fires {@link CutsceneCancelledEvent}) — the simplest
     * policy that's still an explicit, documented rule; a future priority/layer system can replace
     * it without changing this method's signature.
     */
    public static void play(ServerPlayer player, CutsceneDefinition definition, Map<String, Integer> actorEntityIds) {
        if (ACTIVE.containsKey(player.getUUID())) {
            stop(player);
        }
        ACTIVE.put(player.getUUID(), new ActiveCutscene(player, definition, actorEntityIds));
        Network.sendToPlayer(player, new PlayCutscenePacket(definition.id(), actorEntityIds, 0));
        Events.post(new CutsceneStartedEvent(player, definition.id()));
        persist(player, definition.id(), 0, actorEntityIds);
        EngineLog.channel("Cinematic").info("Cutscene '{}' started for {}", definition.id(), player.getGameProfile().getName());
    }

    /** Explicit early stop — a no-op if nothing is active for {@code player}. */
    public static void stop(ServerPlayer player) {
        ActiveCutscene active = ACTIVE.remove(player.getUUID());
        if (active == null) {
            return;
        }
        Network.sendToPlayer(player, new StopCutscenePacket(active.definition.id()));
        Events.post(new CutsceneCancelledEvent(player, active.definition.id()));
        clearPersisted(player);
        EngineLog.channel("Cinematic").info("Cutscene '{}' cancelled for {}", active.definition.id(), player.getGameProfile().getName());
    }

    /**
     * Plays each of {@code definitions} in order for {@code player}, waiting for one to fully
     * complete (via {@link CutsceneCompletedEvent}) before starting the next. Pure sugar over the
     * *existing* {@code Flow.sequence}/{@code Flow.waitForEvent} pattern already demonstrated in
     * {@code CutsceneExampleGallery#playFlowIntegration} — generates and starts a throwaway {@code
     * Flow} under a fresh id, nothing more. Deliberately not a new cinematic sequencing/nesting
     * engine: {@code Cutscene A → TitleCard → Cutscene B} was already achievable by hand with three
     * calls and a couple of {@code Flow.waitForEvent}s; this only removes the boilerplate of
     * writing that out, per {@code ARCHITECTURE.md}'s explicit decision not to build a generic
     * blending/nesting framework.
     */
    public static void playSequence(ServerPlayer player, List<CutsceneDefinition> definitions) {
        if (definitions.isEmpty()) {
            return;
        }
        List<Flow> steps = new ArrayList<>();
        for (CutsceneDefinition definition : definitions) {
            steps.add(Flow.action(ctx -> play(ctx.player(), definition, Map.of())));
            steps.add(Flow.waitForEvent(CutsceneCompletedEvent.class,
                    (ctx, event) -> event.cutsceneId().equals(definition.id()) && event.player().equals(ctx.player())));
        }
        Flow flow = Flow.sequence(steps.toArray(new Flow[0]));
        ResourceLocation flowId = new ResourceLocation(StoryModEngine.MODID, "cinematic_sequence_" + UUID.randomUUID());
        FlowRegistry.register(flowId, flow);
        FlowManager.start(flowId, player);
    }

    /**
     * Restores {@code player}'s in-progress cutscene, if any was saved at their last logout —
     * called from {@code cinematic.persistence.CinematicJoinBridge} on {@code PlayerConnectedEvent},
     * mirroring {@code FlowManager#resumeAll} exactly. Only a cutscene started from a registered id
     * (an {@code @AutoCutscene} field, or anything {@code CutsceneRegistry.register}ed — see {@code
     * CutscenePlaybackData}'s Javadoc) can be restored; anything else was never persisted in the
     * first place, not silently dropped here.
     */
    public static void resumeAll(ServerPlayer player) {
        CutscenePlaybackData data = Capabilities.get(player, ModCinematicCapabilities.PLAYBACK_DATA);
        PersistedCutscene persisted = data == null ? null : data.activeCutscene.orElse(null);
        if (persisted == null) {
            return;
        }
        CutsceneDefinition definition = CutsceneRegistry.get(persisted.definitionId());
        if (definition == null) {
            EngineLog.channel("Cinematic").warn(
                    "resumeAll: '{}' has no cutscene registered under '{}', dropping the saved playback state",
                    player.getGameProfile().getName(), persisted.definitionId());
            data.activeCutscene = Optional.empty();
            return;
        }
        ActiveCutscene active = new ActiveCutscene(player, definition, persisted.actorEntityIds());
        active.elapsedTicks = persisted.tick();
        active.cinematicTime = persisted.tick();
        active.lastFiredTick = persisted.tick();
        ACTIVE.put(player.getUUID(), active);
        Network.sendToPlayer(player, new PlayCutscenePacket(definition.id(), persisted.actorEntityIds(), persisted.tick()));
        EngineLog.channel("Cinematic").info(
                "Resumed cutscene '{}' for {} at tick {}", definition.id(), player.getGameProfile().getName(), persisted.tick());
    }

    private static void persist(ServerPlayer player, ResourceLocation definitionId, int tick, Map<String, Integer> actorEntityIds) {
        CutscenePlaybackData data = Capabilities.get(player, ModCinematicCapabilities.PLAYBACK_DATA);
        if (data == null) {
            return;
        }
        data.activeCutscene = Optional.of(new PersistedCutscene(definitionId, tick, actorEntityIds));
        Capabilities.markDirty(player, ModCinematicCapabilities.PLAYBACK_DATA);
    }

    private static void clearPersisted(ServerPlayer player) {
        CutscenePlaybackData data = Capabilities.get(player, ModCinematicCapabilities.PLAYBACK_DATA);
        if (data == null) {
            return;
        }
        data.activeCutscene = Optional.empty();
        Capabilities.markDirty(player, ModCinematicCapabilities.PLAYBACK_DATA);
    }

    public static boolean isPlaying(ServerPlayer player) {
        return ACTIVE.containsKey(player.getUUID());
    }

    /** The id of whatever cutscene is currently active for {@code player}, or {@code null}. */
    public static ResourceLocation find(ServerPlayer player) {
        ActiveCutscene active = ACTIVE.get(player.getUUID());
        return active == null ? null : active.definition.id();
    }

    /**
     * Starts {@code titleCard} for {@code player}. If one is already active for this player, it's
     * cancelled first (fires {@link TitleCardCancelledEvent}) — the same "one active per player"
     * policy {@link #play} already uses for cutscenes, tracked independently in its own map so a
     * title card and a cutscene never fight over the same slot.
     */
    public static void playTitleCard(ServerPlayer player, TitleCard titleCard) {
        if (ACTIVE_TITLE_CARDS.containsKey(player.getUUID())) {
            cancelTitleCard(player);
        }
        ACTIVE_TITLE_CARDS.put(player.getUUID(), new ActiveTitleCard(player, titleCard));
        Network.sendToPlayer(player, new PlayTitleCardPacket(titleCard));
        Events.post(new TitleCardStartedEvent(player));
        EngineLog.channel("Cinematic").info("TitleCard '{}' started for {}", titleCard.title().getString(), player.getGameProfile().getName());
    }

    /** Explicit early stop — a no-op if no title card is active for {@code player}. */
    public static void cancelTitleCard(ServerPlayer player) {
        ActiveTitleCard active = ACTIVE_TITLE_CARDS.remove(player.getUUID());
        if (active == null) {
            return;
        }
        Network.sendToPlayer(player, new StopTitleCardPacket());
        Events.post(new TitleCardCancelledEvent(player));
        EngineLog.channel("Cinematic").info("TitleCard cancelled for {}", player.getGameProfile().getName());
    }

    public static boolean isPlayingTitleCard(ServerPlayer player) {
        return ACTIVE_TITLE_CARDS.containsKey(player.getUUID());
    }

    /** Advances every active cutscene and title card by one server tick — called once per server tick by {@code CinematicTickBridge}. */
    public static void tick() {
        if (!ACTIVE.isEmpty()) {
            for (ActiveCutscene active : ACTIVE.values().toArray(new ActiveCutscene[0])) {
                try {
                    tickOne(active);
                } catch (Exception e) {
                    EngineLog.channel("Cinematic").error(
                            "Cutscene '" + active.definition.id() + "' for " + active.player.getGameProfile().getName()
                                    + " threw during tick(); dropping it so other players' cutscenes keep running", e);
                    ACTIVE.remove(active.player.getUUID());
                }
            }
        }
        if (!ACTIVE_TITLE_CARDS.isEmpty()) {
            for (ActiveTitleCard active : ACTIVE_TITLE_CARDS.values().toArray(new ActiveTitleCard[0])) {
                try {
                    tickOneTitleCard(active);
                } catch (Exception e) {
                    EngineLog.channel("Cinematic").error(
                            "TitleCard for " + active.player.getGameProfile().getName()
                                    + " threw during tick(); dropping it so other players' title cards keep running", e);
                    ACTIVE_TITLE_CARDS.remove(active.player.getUUID());
                }
            }
        }
    }

    /**
     * Moves the real, server-side entity for every {@link ActorTrack#authoritative()} track on
     * {@code timeline} — the entity's own position/rotation change through the exact same {@code
     * Entity#moveTo} every other moving entity already uses, so Minecraft's existing tracker sync
     * takes care of telling every observing client, with no new packet here. Builds one {@code
     * CutsceneContext} per tick rather than caching it — cheap, and avoids the context ever going
     * stale if a binding's target despawns and respawns mid-cutscene.
     */
    private static void applyAuthoritativeActors(ActiveCutscene active, Timeline timeline) {
        if (timeline.actorTracks().isEmpty()) {
            return;
        }
        CutsceneContext context = new CutsceneContext(active.player.serverLevel(), active.player, active.actorEntityIds);
        for (ActorTrack track : timeline.actorTracks()) {
            if (!track.authoritative()) {
                continue;
            }
            Entity entity = track.binding().resolve(context.level(), context);
            if (entity == null) {
                continue;
            }
            ActorState state = track.evaluate(active.elapsedTicks, 0f);
            state.position().ifPresent(pos -> entity.moveTo(pos.x, pos.y, pos.z));
            state.rotation().ifPresent(rot -> {
                float[] yawPitch = Angles.toYawPitch(rot);
                entity.setYRot(yawPitch[0]);
                entity.setXRot(yawPitch[1]);
                entity.setYHeadRot(yawPitch[0]);
            });
            state.visible().ifPresent(visible -> entity.setInvisible(!visible));
        }
    }

    private static void tickOneTitleCard(ActiveTitleCard active) {
        active.elapsedTicks++;
        if (active.elapsedTicks >= active.titleCard.totalTicks()) {
            ACTIVE_TITLE_CARDS.remove(active.player.getUUID());
            Events.post(new TitleCardCompletedEvent(active.player));
            EngineLog.channel("Cinematic").info("TitleCard completed for {}", active.player.getGameProfile().getName());
        }
    }

    /**
     * Validates and applies one {@code RequestCutsceneControlPacket} — the actual trust boundary:
     * the sender must have {@code cutsceneId} active right now (a stale or spoofed id is ignored),
     * and a *forward* jump (an explicit {@code SEEK} past the current tick, or {@code SKIP}) is
     * refused outright when the timeline's {@link SkipPolicy} is {@link SkipPolicy#NON_SKIPPABLE}
     * — the exact hole a client simply issuing {@code SEEK} to the last tick instead of {@code SKIP}
     * would otherwise open. {@code PAUSE}/{@code RESUME}/{@code SPEED}/backward {@code SEEK} are
     * always allowed: none of them can skip content a player hasn't already reached — speeding up
     * still fires every cue in between (see the range-scan in {@link #tickOne}), it just compresses
     * *how fast* time passes, never *how much* of the timeline is evaluated. Always replies with
     * {@link SyncCutscenePlaybackPacket} reflecting what was actually applied, even a rejection.
     */
    public static void handleControlRequest(ServerPlayer player, ResourceLocation cutsceneId, CutsceneControlAction action, int tickArg, float speedArg) {
        ActiveCutscene active = ACTIVE.get(player.getUUID());
        if (active == null || !active.definition.id().equals(cutsceneId)) {
            EngineLog.channel("Cinematic").warn(
                    "handleControlRequest({}): '{}' has no such cutscene active, ignoring", action, player.getGameProfile().getName());
            return;
        }
        Timeline timeline = active.definition.timeline();
        int lastTick = Math.max(0, timeline.durationTicks() - 1);

        switch (action) {
            case PAUSE -> active.paused = true;
            case RESUME -> active.paused = false;
            case SPEED -> active.speed = Mth.clamp(speedArg, 0.01f, 8f);
            case SEEK -> {
                int target = Mth.clamp(tickArg, 0, lastTick);
                if (target > active.elapsedTicks && timeline.skipPolicy() == SkipPolicy.NON_SKIPPABLE) {
                    EngineLog.channel("Cinematic").warn(
                            "handleControlRequest(SEEK): '{}' is NON_SKIPPABLE, refusing a forward seek from {}", cutsceneId, player.getGameProfile().getName());
                } else {
                    seekActive(active, target);
                }
            }
            case SKIP -> {
                if (timeline.skipPolicy() == SkipPolicy.NON_SKIPPABLE) {
                    EngineLog.channel("Cinematic").warn(
                            "handleControlRequest(SKIP): '{}' is NON_SKIPPABLE, refusing skip from {}", cutsceneId, player.getGameProfile().getName());
                } else if (timeline.skipPolicy() == SkipPolicy.SKIP_TO_MARKER && timeline.defaultSkipMarker() != null
                        && timeline.markerTick(timeline.defaultSkipMarker()) != null) {
                    seekActive(active, timeline.markerTick(timeline.defaultSkipMarker()));
                } else {
                    seekActive(active, lastTick);
                }
            }
        }

        Network.sendToPlayer(player, new SyncCutscenePlaybackPacket(cutsceneId, active.elapsedTicks, active.speed, active.paused));
    }

    private static void seekActive(ActiveCutscene active, int tick) {
        active.elapsedTicks = tick;
        active.cinematicTime = tick;
        active.lastFiredTick = tick;
    }

    private static void tickOne(ActiveCutscene active) {
        if (active.paused) {
            return;
        }
        Timeline timeline = active.definition.timeline();
        int duration = timeline.durationTicks();
        active.cinematicTime += active.speed;
        int previousTick = active.elapsedTicks;
        active.elapsedTicks = Math.min((int) active.cinematicTime, duration);

        applyAuthoritativeActors(active, timeline);

        // Scans every tick crossed since the last one checked, not just the latest — speed > 1 can
        // advance several ticks in one call, and without this a fast-forwarded cutscene could
        // silently skip a Trigger/EventTrack/ActionTrack cue. seekActive() resyncs lastFiredTick
        // directly (without scanning) so an actual seek/skip still never replays anything, matching
        // the exact same rule the client's own audio-cue firing already established.
        for (int t = active.lastFiredTick + 1; t <= active.elapsedTicks; t++) {
            for (Trigger trigger : timeline.triggers()) {
                if (trigger.tick() == t) {
                    Events.post(new CutsceneTriggerEvent(active.player, active.definition.id(), trigger.id()));
                    EngineLog.channel("Cinematic").debug("Trigger '{}' fired for '{}'", trigger.id(), active.definition.id());
                }
            }
            for (EventTrack.EventCue cue : timeline.events().cues()) {
                if (cue.tick() == t) {
                    try {
                        Events.post(cue.event().get());
                    } catch (Exception e) {
                        EngineLog.channel("Cinematic").error(
                                "EventTrack cue at tick " + cue.tick() + " on '" + active.definition.id() + "' threw", e);
                    }
                }
            }
            for (ActionTrack.ActionCue cue : timeline.actions().cues()) {
                if (cue.tick() == t) {
                    try {
                        cue.action().accept(active.player);
                    } catch (Exception e) {
                        EngineLog.channel("Cinematic").error(
                                "ActionTrack cue at tick " + cue.tick() + " on '" + active.definition.id() + "' threw", e);
                    }
                }
            }
        }
        active.lastFiredTick = active.elapsedTicks;

        if (active.elapsedTicks >= duration) {
            ACTIVE.remove(active.player.getUUID());
            Events.post(new CutsceneCompletedEvent(active.player, active.definition.id()));
            clearPersisted(active.player);
            EngineLog.channel("Cinematic").info(
                    "Cutscene '{}' completed for {}", active.definition.id(), active.player.getGameProfile().getName());
        } else if (active.elapsedTicks / PERSIST_INTERVAL_TICKS != previousTick / PERSIST_INTERVAL_TICKS) {
            // Not every tick — see CutscenePlaybackData's Javadoc: a relog resumes at roughly the
            // last-persisted tick, not necessarily the exact one, which is an acceptable trade-off
            // against writing this capability field 20x/sec for every active cutscene.
            persist(active.player, active.definition.id(), active.elapsedTicks, active.actorEntityIds);
        }
    }

    /** How often (in ticks) an in-progress cutscene's resume point is checkpointed — see {@link #tickOne}. */
    private static final int PERSIST_INTERVAL_TICKS = 20;

    private static final class ActiveCutscene {
        final ServerPlayer player;
        final CutsceneDefinition definition;
        final Map<String, Integer> actorEntityIds;
        int elapsedTicks;
        float cinematicTime;
        int lastFiredTick;
        boolean paused;
        float speed = 1f;

        ActiveCutscene(ServerPlayer player, CutsceneDefinition definition, Map<String, Integer> actorEntityIds) {
            this.player = player;
            this.definition = definition;
            this.actorEntityIds = actorEntityIds;
        }
    }

    private static final class ActiveTitleCard {
        final ServerPlayer player;
        final TitleCard titleCard;
        int elapsedTicks;

        ActiveTitleCard(ServerPlayer player, TitleCard titleCard) {
            this.player = player;
            this.titleCard = titleCard;
        }
    }
}
