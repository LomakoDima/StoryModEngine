package com.dimalab.storymodengine.client.cinematic;

import com.dimalab.storymodengine.common.StoryModEngine;
import com.dimalab.storymodengine.common.cinematic.CutsceneContext;
import com.dimalab.storymodengine.common.cinematic.CutsceneDefinition;
import com.dimalab.storymodengine.common.cinematic.CutsceneRuntime;
import com.dimalab.storymodengine.api.cinematic.PlaybackState;
import com.dimalab.storymodengine.common.cinematic.Shot;
import com.dimalab.storymodengine.common.cinematic.Timeline;
import com.dimalab.storymodengine.common.cinematic.binding.LookAt;
import com.dimalab.storymodengine.common.cinematic.registry.CutsceneRegistry;
import com.dimalab.storymodengine.common.cinematic.state.ActorState;
import com.dimalab.storymodengine.common.cinematic.state.AudioCue;
import com.dimalab.storymodengine.common.cinematic.state.CameraState;
import com.dimalab.storymodengine.common.cinematic.state.FadeState;
import com.dimalab.storymodengine.common.cinematic.state.Subtitle;
import com.dimalab.storymodengine.common.cinematic.track.ActorTrack;
import com.dimalab.storymodengine.common.logging.EngineLog;
import com.dimalab.storymodengine.api.math.Angles;
import com.dimalab.storymodengine.api.math.interp.Interpolators;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Map;
import java.util.Optional;

/**
 * The single client-side driver for whichever cutscene is currently playing — owns one {@link
 * CutsceneRuntime}, one {@link CameraRig}, and orchestrates every other {@code cinematic.client}
 * applier. Everything here runs purely from the deterministic {@link CutsceneDefinition} already
 * loaded locally (via {@code @AutoCutscene}); after the one {@code PlayCutscenePacket} that starts
 * it, nothing more is ever received from the server for this cutscene's normal playback.
 *
 * <p>Two separate cadences, matching {@code Track}'s own contract: {@link #onClientTick} advances
 * the timeline and applies one-shot per-tick effects (audio cues, subtitle text, actor pose —
 * cheap, no partial-tick smoothing needed for any of those); {@link #onRenderTickStart} runs once
 * per render *frame* and is the only place camera position/rotation/roll/FOV get sampled at
 * partial-tick resolution, via {@link CameraRig} — otherwise the camera would visibly step at 20Hz
 * instead of the render framerate.
 *
 * <p>Playback control ({@link #pause}/{@link #resume}/{@link #seek}/{@link #jumpTo}/{@link
 * #setSpeed}/{@link #skip}) is exposed here as the one place client-only cutscene state actually
 * lives — {@code cinematic.client.CutsceneControlCommands} calls straight into these.
 */
@Mod.EventBusSubscriber(modid = StoryModEngine.MODID, value = Dist.CLIENT)
public final class ClientCutscenePlayer {

    private static final CameraRig CAMERA = new CameraRig();
    private static CutsceneRuntime runtime;
    private static Subtitle lastSubtitle;
    private static float lastFov = 70f;
    private static float lastRoll = 0f;
    private static Shot currentShot;
    private static Shot enteringFromShot;
    private static int lastAppliedAudioTick = -1;

    private ClientCutscenePlayer() {
    }

    public static void play(ResourceLocation cutsceneId, Map<String, Integer> actorEntityIds, int startTick) {
        CutsceneDefinition definition = CutsceneRegistry.get(cutsceneId);
        if (definition == null) {
            EngineLog.channel("Cinematic").warn("Received PlayCutscenePacket for unknown '{}', ignoring", cutsceneId);
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            EngineLog.channel("Cinematic").warn("Cannot play '{}' — no local player/level yet", cutsceneId);
            return;
        }
        if (runtime != null && runtime.isPlaying()) {
            stopInternal();
        }

        CutsceneContext context = new CutsceneContext(minecraft.level, minecraft.player, actorEntityIds);
        runtime = new CutsceneRuntime(definition, context);
        CAMERA.start(minecraft.level);
        currentShot = null;
        enteringFromShot = null;
        lastAppliedAudioTick = -1;
        runtime.play();
        if (startTick > 0) {
            // Resuming after a reconnect (see CinematicManager#resumeAll) — jump straight to where
            // the player left off, reusing the exact same seek() a player-issued command uses.
            // lastAppliedAudioTick is set to match so the resume itself doesn't replay every audio
            // cue between tick 0 and startTick (the same "a seek never spams" rule seek() already
            // guarantees elsewhere).
            runtime.seek(startTick);
            lastAppliedAudioTick = runtime.instance().currentTick();
        }
    }

    public static void stop(ResourceLocation cutsceneId) {
        if (runtime != null && runtime.instance().definition().id().equals(cutsceneId)) {
            stopInternal();
        }
    }

    private static void stopInternal() {
        runtime.stop();
        CAMERA.stop();
        SubtitleOverlay.clear();
        FadeOverlay.clear();
        lastSubtitle = null;
        currentShot = null;
        enteringFromShot = null;
        runtime = null;
    }

    /** Whether some cutscene is currently active client-side — {@code cinematic.client.CutsceneControlCommands} uses this to report "nothing playing". */
    public static boolean isActive() {
        return runtime != null;
    }

    public static ResourceLocation activeCutsceneId() {
        return runtime == null ? null : runtime.instance().definition().id();
    }

    public static void pause() {
        if (runtime != null) {
            runtime.pause();
        }
    }

    public static void resume() {
        if (runtime != null) {
            runtime.resume();
        }
    }

    public static void setSpeed(float speed) {
        if (runtime != null) {
            runtime.setSpeed(speed);
        }
    }

    /** See {@code CutsceneInstance#seek(int)} — repositions without replaying anything skipped over. */
    public static void seek(int tick) {
        if (runtime == null) {
            return;
        }
        runtime.seek(tick);
        lastAppliedAudioTick = runtime.instance().currentTick();
    }

    public static void jumpTo(String markerName) {
        if (runtime == null) {
            return;
        }
        runtime.jumpTo(markerName);
        lastAppliedAudioTick = runtime.instance().currentTick();
    }

    public static void skip() {
        if (runtime == null) {
            return;
        }
        runtime.skip();
        lastAppliedAudioTick = runtime.instance().currentTick();
    }

    /**
     * Snaps local playback to the server's authoritative answer to a {@code
     * RequestCutsceneControlPacket} — {@code cinematic.client.CutsceneControlCommands} no longer
     * mutates this class directly; it only ever sends a request and waits for this. A mismatched or
     * stale {@code cutsceneId} (e.g. this cutscene already ended locally by the time the reply
     * arrives) is silently ignored, not applied to whatever plays next.
     */
    public static void applySync(ResourceLocation cutsceneId, int tick, float speed, boolean paused) {
        if (runtime == null || !runtime.instance().definition().id().equals(cutsceneId)) {
            return;
        }
        seek(tick);
        runtime.setSpeed(speed);
        if (paused) {
            runtime.pause();
        } else {
            runtime.resume();
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || runtime == null || !runtime.isPlaying()) {
            return;
        }
        try {
            tickOnce();
        } catch (Exception e) {
            EngineLog.channel("Cinematic").error("Cutscene playback threw during client tick — stopping it", e);
            stopInternal();
        }
    }

    private static void tickOnce() {
        int tick = runtime.instance().currentTick();
        CutsceneContext context = runtime.instance().context();
        Timeline timeline = runtime.instance().definition().timeline();

        boolean transitionFadeActive = applyCamera(timeline, context, tick);

        for (ActorTrack track : timeline.actorTracks()) {
            if (track.authoritative()) {
                // CinematicManager already moves the real entity server-side for this track —
                // applying our own local override on top would fight it every frame. Vanilla's
                // normal entity render-interpolation smooths whatever the server already sent.
                continue;
            }
            Entity entity = track.binding().resolve(context.level(), context);
            ActorState state = track.evaluate(tick, 0f);
            ActorApplier.apply(entity, state);
        }

        Subtitle subtitle = timeline.subtitles().evaluate(tick, 0f).orElse(null);
        if (subtitle != null) {
            SubtitleOverlay.show(subtitle, subtitle.opacityAt(tick));
        } else if (lastSubtitle != null) {
            SubtitleOverlay.clear();
        }
        lastSubtitle = subtitle;

        Optional<FadeState> authoredFade = timeline.fades().evaluate(tick, 0f);
        if (authoredFade.isPresent()) {
            FadeOverlay.show(authoredFade.get());
        } else if (!transitionFadeActive) {
            FadeOverlay.clear();
        }

        // Speed can advance more than one tick per call — scan every tick crossed (not just the
        // latest) so a fast-forwarded cutscene can't silently skip a cue. Landing on a tick via
        // seek()/jumpTo()/skip() instead resyncs lastAppliedAudioTick without scanning — see those
        // methods — so scrubbing never replays a cue this way either.
        for (int t = lastAppliedAudioTick + 1; t <= tick; t++) {
            timeline.audio().evaluate(t, 0f).ifPresent(AudioApplier::play);
        }
        lastAppliedAudioTick = tick;

        runtime.tickOnce();

        PlaybackState state = runtime.state();
        if (state == PlaybackState.COMPLETED || state == PlaybackState.CANCELLED) {
            stopInternal();
        }
    }

    /** Evaluates the active {@link Shot}'s camera (with LookAt/transition applied) and feeds it to {@link #CAMERA}. Returns whether a {@code FADE} transition is rendering this tick. */
    private static boolean applyCamera(Timeline timeline, CutsceneContext context, int tick) {
        Shot shot = timeline.activeShot(tick);
        if (shot != currentShot) {
            enteringFromShot = currentShot;
            currentShot = shot;
        }
        if (shot == null) {
            return false;
        }

        CameraState state = shot.camera().evaluate(tick, 0f);
        int sinceStart = tick - shot.startTick();
        boolean inTransition = enteringFromShot != null && shot.transitionTicks() > 0 && sinceStart < shot.transitionTicks();

        if (inTransition && shot.transition() == Shot.Transition.CROSSFADE) {
            CameraState fromState = enteringFromShot.camera().evaluate(tick, 0f);
            float t = Mth.clamp((sinceStart + 1f) / shot.transitionTicks(), 0f, 1f);
            state = blend(fromState, state, t);
        }

        Quaternionf lookAtRotation = resolveLookAt(shot, context, state.position());
        if (lookAtRotation != null) {
            state = new CameraState(state.position(), lookAtRotation, state.fov(), state.roll());
        }

        CAMERA.tick(state);

        boolean transitionFadeActive = false;
        if (inTransition && shot.transition() == Shot.Transition.FADE) {
            float half = shot.transitionTicks() / 2f;
            float opacity = Mth.clamp(1f - Math.abs((sinceStart + 1f) - half) / half, 0f, 1f);
            FadeOverlay.show(new FadeState(0x000000, opacity));
            transitionFadeActive = true;
        }
        return transitionFadeActive;
    }

    private static Quaternionf resolveLookAt(Shot shot, CutsceneContext context, Vector3f cameraPosition) {
        LookAt lookAt = shot.camera().lookAt();
        if (lookAt == null) {
            return null;
        }
        Vector3f target = lookAt.resolve(context);
        return target == null ? null : Angles.lookAt(cameraPosition, target);
    }

    private static CameraState blend(CameraState a, CameraState b, float t) {
        return new CameraState(
                Interpolators.VECTOR3F.interpolate(a.position(), b.position(), t),
                Interpolators.QUATERNION_SLERP.interpolate(a.rotation(), b.rotation(), t),
                Interpolators.FLOAT.interpolate(a.fov(), b.fov(), t),
                Interpolators.FLOAT.interpolate(a.roll(), b.roll(), t));
    }

    @SubscribeEvent
    public static void onRenderTickStart(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START || runtime == null || !runtime.isPlaying()) {
            return;
        }
        float partialTick = Minecraft.getInstance().getFrameTime();
        lastFov = CAMERA.renderFrame(partialTick);
        lastRoll = CAMERA.roll(partialTick);
    }

    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (CAMERA.isActive()) {
            event.setFOV(lastFov);
        }
    }

    /** Applies camera roll — the one channel with no {@code Entity} field to drive, same reasoning as {@link #onComputeFov}. */
    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (CAMERA.isActive()) {
            event.setRoll(lastRoll);
        }
    }

    /**
     * Whether the first-person hand/held-item overlay renders is driven by {@code Options
     * .getCameraType()} (the F5 view-mode toggle) — not by which entity the camera is actually
     * attached to. Redirecting the camera to the rig via {@code CameraRig} does nothing to that
     * flag, so without this the local player's hand keeps drawing in the foreground the entire
     * time, even though the view itself is genuinely coming from the rig. Cancelling this event
     * is the direct, documented way to suppress it independent of the view-mode setting.
     */
    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (CAMERA.isActive()) {
            event.setCanceled(true);
        }
    }
}
