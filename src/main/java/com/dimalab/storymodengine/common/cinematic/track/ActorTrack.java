package com.dimalab.storymodengine.common.cinematic.track;

import com.dimalab.storymodengine.common.cinematic.actor.ActorBinding;
import com.dimalab.storymodengine.common.cinematic.keyframe.KeyframeTrack;
import com.dimalab.storymodengine.common.cinematic.state.ActorState;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Optional;

/**
 * Position, rotation, and visibility for one bound entity — {@link #binding()} says *which* entity
 * (resolved separately, per-play, by {@code cinematic.client.ActorApplier} or, when {@link
 * #authoritative()}, by {@code CinematicManager} server-side); this class only ever produces pure
 * {@link ActorState} values, never touches an {@code Entity} itself. Each channel is independently
 * optional (a track built with only a rotation keyframe leaves {@code ActorState.position()} empty)
 * — see {@link ActorState}'s Javadoc for why.
 *
 * <p>{@link #authoritative()} defaults to {@code false} — the original, purely cosmetic,
 * client-only behavior every existing example still uses unchanged. When {@code true}, {@code
 * CinematicManager#tickOne} additionally moves the *real* server-side entity each tick (via {@code
 * Entity#moveTo}, which piggybacks on Minecraft's own existing entity-tracking sync — no new
 * network packet needed), and {@code ClientCutscenePlayer} skips its own local {@code ActorApplier}
 * call for this track so the two don't fight over the same entity — both sides already know which
 * mode a track is in, since both evaluate the identical {@code Timeline} object graph.
 */
public final class ActorTrack implements Track<ActorState> {

    private final ActorBinding binding;
    private final KeyframeTrack<Vector3f> position;
    private final KeyframeTrack<Quaternionf> rotation;
    private final KeyframeTrack<Boolean> visibility;
    private final boolean authoritative;

    public ActorTrack(ActorBinding binding, KeyframeTrack<Vector3f> position, KeyframeTrack<Quaternionf> rotation,
                       KeyframeTrack<Boolean> visibility, boolean authoritative) {
        this.binding = binding;
        this.position = position;
        this.rotation = rotation;
        this.visibility = visibility;
        this.authoritative = authoritative;
    }

    public ActorBinding binding() {
        return binding;
    }

    public boolean authoritative() {
        return authoritative;
    }

    @Override
    public ActorState evaluate(int tick, float partialTick) {
        return new ActorState(
                position == null ? Optional.empty() : Optional.of(position.evaluate(tick, partialTick)),
                rotation == null ? Optional.empty() : Optional.of(rotation.evaluate(tick, partialTick)),
                visibility == null ? Optional.empty() : Optional.of(visibility.evaluate(tick, partialTick)));
    }
}
