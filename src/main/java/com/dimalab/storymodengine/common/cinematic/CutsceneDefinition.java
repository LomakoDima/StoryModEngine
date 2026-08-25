package com.dimalab.storymodengine.common.cinematic;

import com.dimalab.storymodengine.api.cinematic.SkipPolicy;
import com.dimalab.storymodengine.common.cinematic.actor.ActorBinding;
import com.dimalab.storymodengine.common.cinematic.binding.LookAt;
import com.dimalab.storymodengine.common.cinematic.clip.Clip;
import com.dimalab.storymodengine.common.cinematic.keyframe.Keyframe;
import com.dimalab.storymodengine.common.cinematic.keyframe.KeyframeTrack;
import com.dimalab.storymodengine.common.cinematic.state.FadeState;
import com.dimalab.storymodengine.common.cinematic.state.Subtitle;
import com.dimalab.storymodengine.common.cinematic.track.ActionTrack;
import com.dimalab.storymodengine.common.cinematic.track.ActorTrack;
import com.dimalab.storymodengine.common.cinematic.track.AudioTrack;
import com.dimalab.storymodengine.common.cinematic.track.CameraTrack;
import com.dimalab.storymodengine.common.cinematic.track.EventTrack;
import com.dimalab.storymodengine.common.cinematic.track.FadeTrack;
import com.dimalab.storymodengine.common.cinematic.track.SubtitleTrack;
import com.dimalab.storymodengine.common.cinematic.track.Track;
import com.dimalab.storymodengine.api.event.Event;
import com.dimalab.storymodengine.api.math.Angles;
import com.dimalab.storymodengine.api.math.interp.Easing;
import com.dimalab.storymodengine.api.math.interp.Interpolator;
import com.dimalab.storymodengine.api.math.interp.Interpolators;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * An immutable, named description of a cinematic — nothing about how many times it's played, by
 * whom, or with which actor bindings lives here (see {@link CutsceneInstance}/{@link
 * CutsceneContext} for that). A definition can be replayed by any number of players, any number of
 * times, simultaneously.
 *
 * <pre>{@code
 * import static com.dimalab.storymodengine.common.cinematic.CutsceneDefinition.pos;
 * import static com.dimalab.storymodengine.common.cinematic.CutsceneDefinition.rot;
 *
 * public static final CutsceneDefinition INTRO = CutsceneDefinition.define(id("intro"))
 *         .duration(200)
 *         .shot(0, 100, camera -> camera
 *                 .position(0, pos(0, 2, 3))
 *                 .position(40, pos(0, 2, 1))
 *                 .rotation(0, rot(0, 0)))
 *         .actor("npc", actor -> actor.rotation(60, rot(180, 0)))
 *         .subtitle("You're finally here.", 70, 30)
 *         .build();
 * }</pre>
 *
 * Coordinate/time convention: every tick number is a **cinematic tick** relative to the cutscene's
 * own start (tick 0 = the moment it began playing), matching Minecraft's own 20-ticks-per-second
 * model — not wall-clock time, and never mixed with world tick counters directly (see {@code
 * CutsceneInstance}). Positions/rotations are absolute world-space {@code Vector3f}/{@code
 * Quaternionf}, exactly like every other {@code math} consumer in this engine.
 */
public final class CutsceneDefinition {

    private final ResourceLocation id;
    private final Timeline timeline;

    private CutsceneDefinition(ResourceLocation id, Timeline timeline) {
        this.id = id;
        this.timeline = timeline;
    }

    public static Builder define(ResourceLocation id) {
        return new Builder(id);
    }

    public ResourceLocation id() {
        return id;
    }

    public Timeline timeline() {
        return timeline;
    }

    @Override
    public String toString() {
        return "CutsceneDefinition[" + id + "]";
    }

    public static final class Builder {

        private final ResourceLocation id;
        private int durationTicks = -1;
        private final List<Shot> shots = new ArrayList<>();
        private final List<ActorTrack> actorTracks = new ArrayList<>();
        private final List<Subtitle> subtitles = new ArrayList<>();
        private final List<AudioTrack.Cue> audioCues = new ArrayList<>();
        private final List<Clip<FadeState>> fadeClips = new ArrayList<>();
        private final List<Trigger> triggers = new ArrayList<>();
        private final List<EventTrack.EventCue> eventCues = new ArrayList<>();
        private final List<ActionTrack.ActionCue> actionCues = new ArrayList<>();
        private final List<CutsceneMarker> markers = new ArrayList<>();
        private SkipPolicy skipPolicy = SkipPolicy.SKIPPABLE;
        private String defaultSkipMarker;

        private Builder(ResourceLocation id) {
            this.id = id;
        }

        public Builder duration(int ticks) {
            this.durationTicks = ticks;
            return this;
        }

        public Builder shot(int startTick, int durationTicks, Consumer<CameraTrackBuilder> camera) {
            return shot(startTick, durationTicks, Shot.Transition.CUT, 0, camera);
        }

        /** Same as {@link #shot(int, int, Consumer)}, plus how this shot transitions in from whatever preceded it — see {@link Shot.Transition}. */
        public Builder shot(int startTick, int durationTicks, Shot.Transition transition, int transitionTicks, Consumer<CameraTrackBuilder> camera) {
            CameraTrackBuilder builder = new CameraTrackBuilder();
            camera.accept(builder);
            shots.add(new Shot(startTick, durationTicks, builder.build(), transition, transitionTicks));
            return this;
        }

        public Builder actor(String bindingName, Consumer<ActorTrackBuilder> actor) {
            ActorTrackBuilder builder = new ActorTrackBuilder();
            actor.accept(builder);
            actorTracks.add(builder.build(ActorBinding.named(bindingName)));
            return this;
        }

        public Builder subtitle(String text, int startTick, int durationTicks) {
            return subtitle(text, startTick, durationTicks, null);
        }

        public Builder subtitle(String text, int startTick, int durationTicks, String speaker) {
            subtitles.add(new Subtitle(text, startTick, startTick + durationTicks, speaker));
            return this;
        }

        /** Full control over fade in/out and styling — see {@link Subtitle}'s Javadoc for what each field means. */
        public Builder subtitle(String text, int startTick, int durationTicks, String speaker,
                                 int fadeInTicks, int fadeOutTicks, int rgbColor, Subtitle.Position position) {
            subtitles.add(new Subtitle(text, startTick, startTick + durationTicks, speaker,
                    fadeInTicks, fadeOutTicks, rgbColor, position));
            return this;
        }

        public Builder sound(int tick, SoundEvent sound, float volume, float pitch) {
            audioCues.add(new AudioTrack.Cue(tick, sound, volume, pitch));
            return this;
        }

        /** Same as {@link #sound(int, SoundEvent, float, float)}, played as a positioned world sound instead of "on" the viewer. */
        public Builder sound(int tick, SoundEvent sound, float volume, float pitch, Vector3f position) {
            audioCues.add(new AudioTrack.Cue(tick, sound, volume, pitch, position));
            return this;
        }

        /** A full-screen color fade, opacity ramping linearly from {@code fromOpacity} to {@code toOpacity} over {@code [startTick, startTick + durationTicks)}. */
        public Builder fade(int startTick, int durationTicks, int rgbColor, float fromOpacity, float toOpacity) {
            return fade(startTick, durationTicks, rgbColor, fromOpacity, toOpacity, Easing.LINEAR);
        }

        public Builder fade(int startTick, int durationTicks, int rgbColor, float fromOpacity, float toOpacity, Easing easing) {
            if (durationTicks <= 0) {
                throw new IllegalStateException("Cutscene '" + id + "': fade duration must be positive");
            }
            Track<FadeState> content = (localTick, partialTick) -> {
                float t = Mth.clamp((localTick + partialTick) / durationTicks, 0f, 1f);
                float opacity = Interpolators.FLOAT.interpolate(fromOpacity, toOpacity, easing.apply(t));
                return new FadeState(rgbColor, opacity);
            };
            fadeClips.add(new Clip<>(startTick, durationTicks, content));
            return this;
        }

        public Builder trigger(int tick, ResourceLocation triggerId) {
            triggers.add(new Trigger(tick, triggerId));
            return this;
        }

        /** Posts {@code event.get()} to the local {@code EventBus} exactly once, on {@code tick} — see {@link EventTrack}. */
        public Builder event(int tick, Supplier<Event> event) {
            eventCues.add(new EventTrack.EventCue(tick, event));
            return this;
        }

        /** Runs {@code action} against the playing {@code ServerPlayer} exactly once, on {@code tick}, server-side only — see {@link ActionTrack}. */
        public Builder action(int tick, Consumer<ServerPlayer> action) {
            actionCues.add(new ActionTrack.ActionCue(tick, action));
            return this;
        }

        /** Names {@code tick} — {@code CutsceneRuntime#jumpTo(name)} and {@link SkipPolicy#SKIP_TO_MARKER} resolve through this. */
        public Builder marker(String name, int tick) {
            markers.add(new CutsceneMarker(name, tick));
            return this;
        }

        public Builder skipPolicy(SkipPolicy policy) {
            this.skipPolicy = policy;
            return this;
        }

        /** The marker {@link SkipPolicy#SKIP_TO_MARKER} jumps to — only meaningful alongside that policy. */
        public Builder defaultSkipMarker(String markerName) {
            this.defaultSkipMarker = markerName;
            return this;
        }

        public CutsceneDefinition build() {
            if (durationTicks <= 0) {
                throw new IllegalStateException("Cutscene '" + id + "': duration must be set to a positive tick count");
            }
            if (shots.isEmpty()) {
                throw new IllegalStateException("Cutscene '" + id + "': needs at least one shot() — call duration(...).shot(...) before build()");
            }
            Timeline timeline = new Timeline(durationTicks, shots, actorTracks,
                    new SubtitleTrack(subtitles), new AudioTrack(audioCues), new FadeTrack(fadeClips), triggers,
                    new EventTrack(eventCues), new ActionTrack(actionCues), markers, skipPolicy, defaultSkipMarker);
            return new CutsceneDefinition(id, timeline);
        }
    }

    /**
     * Builds one {@link CameraTrack} — position/rotation default to nothing keyframed only if
     * never called (an error, since a shot needs at least a position); FOV/roll default to a
     * constant 70°/0° if never keyframed.
     */
    public static final class CameraTrackBuilder {

        private final List<Keyframe<Vector3f>> position = new ArrayList<>();
        private final List<Keyframe<Quaternionf>> rotation = new ArrayList<>();
        private final List<Keyframe<Float>> fov = new ArrayList<>();
        private final List<Keyframe<Float>> roll = new ArrayList<>();
        private LookAt lookAt;

        public CameraTrackBuilder position(int tick, Vector3f value) {
            return position(tick, value, Interpolators.VECTOR3F);
        }

        public CameraTrackBuilder position(int tick, Vector3f value, Interpolator<Vector3f> interpolator) {
            position.add(new Keyframe<>(tick, value, interpolator));
            return this;
        }

        public CameraTrackBuilder rotation(int tick, Quaternionf value) {
            return rotation(tick, value, Interpolators.QUATERNION_SLERP);
        }

        public CameraTrackBuilder rotation(int tick, Quaternionf value, Interpolator<Quaternionf> interpolator) {
            rotation.add(new Keyframe<>(tick, value, interpolator));
            return this;
        }

        public CameraTrackBuilder fov(int tick, float value) {
            return fov(tick, value, Interpolators.FLOAT);
        }

        public CameraTrackBuilder fov(int tick, float value, Interpolator<Float> interpolator) {
            fov.add(new Keyframe<>(tick, value, interpolator));
            return this;
        }

        /** Camera roll in degrees — positive/negative sign matches {@code Angles.fromYawPitchRoll}. */
        public CameraTrackBuilder roll(int tick, float degrees) {
            return roll(tick, degrees, Interpolators.FLOAT);
        }

        public CameraTrackBuilder roll(int tick, float degrees, Interpolator<Float> interpolator) {
            roll.add(new Keyframe<>(tick, degrees, interpolator));
            return this;
        }

        /** Overrides this shot's rotation to always face {@code target} — see {@link LookAt}. Coexists with, and overrides, {@link #rotation}. */
        public CameraTrackBuilder lookAt(LookAt target) {
            this.lookAt = target;
            return this;
        }

        private CameraTrack build() {
            if (position.isEmpty()) {
                throw new IllegalStateException("A camera shot needs at least one position(...) keyframe");
            }
            if (rotation.isEmpty()) {
                throw new IllegalStateException("A camera shot needs at least one rotation(...) keyframe");
            }
            List<Keyframe<Float>> fovOrDefault = fov.isEmpty()
                    ? List.of(new Keyframe<>(0, 70f, Interpolators.FLOAT))
                    : fov;
            List<Keyframe<Float>> rollOrDefault = roll.isEmpty()
                    ? List.of(new Keyframe<>(0, 0f, Interpolators.FLOAT))
                    : roll;
            return new CameraTrack(KeyframeTrack.of(position), KeyframeTrack.of(rotation),
                    KeyframeTrack.of(fovOrDefault), KeyframeTrack.of(rollOrDefault), lookAt);
        }
    }

    /** Builds one {@link ActorTrack} — each channel left un-called stays {@code null} (see {@code ActorState}'s Javadoc: an untouched channel, not a snapped-to-default one). */
    public static final class ActorTrackBuilder {

        private final List<Keyframe<Vector3f>> position = new ArrayList<>();
        private final List<Keyframe<Quaternionf>> rotation = new ArrayList<>();
        private final List<Keyframe<Boolean>> visibility = new ArrayList<>();
        private boolean authoritative = false;

        public ActorTrackBuilder position(int tick, Vector3f value) {
            return position(tick, value, Interpolators.VECTOR3F);
        }

        public ActorTrackBuilder position(int tick, Vector3f value, Interpolator<Vector3f> interpolator) {
            position.add(new Keyframe<>(tick, value, interpolator));
            return this;
        }

        public ActorTrackBuilder rotation(int tick, Quaternionf value) {
            return rotation(tick, value, Interpolators.QUATERNION_SLERP);
        }

        public ActorTrackBuilder rotation(int tick, Quaternionf value, Interpolator<Quaternionf> interpolator) {
            rotation.add(new Keyframe<>(tick, value, interpolator));
            return this;
        }

        public ActorTrackBuilder visible(int tick, boolean value) {
            visibility.add(new Keyframe<>(tick, value, Interpolators.step()));
            return this;
        }

        /**
         * Makes this track server-authoritative: {@code CinematicManager} moves the *real* bound
         * entity server-side each tick (via {@code Entity#moveTo}), instead of the position/
         * rotation channels only ever being a client-local visual override. Defaults to {@code
         * false} — every existing cutscene keeps its current, purely cosmetic behavior unless it
         * opts in.
         */
        public ActorTrackBuilder authoritative() {
            this.authoritative = true;
            return this;
        }

        private ActorTrack build(ActorBinding binding) {
            KeyframeTrack<Vector3f> positionTrack = position.isEmpty() ? null : KeyframeTrack.of(position);
            KeyframeTrack<Quaternionf> rotationTrack = rotation.isEmpty() ? null : KeyframeTrack.of(rotation);
            KeyframeTrack<Boolean> visibilityTrack = visibility.isEmpty() ? null : KeyframeTrack.of(visibility);
            return new ActorTrack(binding, positionTrack, rotationTrack, visibilityTrack, authoritative);
        }
    }

    /** Convenience — {@code new Vector3f(x, y, z)}, so a definition doesn't need a JOML import of its own just for this. */
    public static Vector3f pos(double x, double y, double z) {
        return new Vector3f((float) x, (float) y, (float) z);
    }

    /** Convenience — a rotation from yaw/pitch degrees (Minecraft's own convention), via {@code Angles}. */
    public static Quaternionf rot(float yawDegrees, float pitchDegrees) {
        return Angles.fromYawPitch(yawDegrees, pitchDegrees);
    }
}
