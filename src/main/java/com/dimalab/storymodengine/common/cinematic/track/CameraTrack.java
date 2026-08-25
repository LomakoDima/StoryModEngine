package com.dimalab.storymodengine.common.cinematic.track;

import com.dimalab.storymodengine.common.cinematic.binding.LookAt;
import com.dimalab.storymodengine.common.cinematic.keyframe.KeyframeTrack;
import com.dimalab.storymodengine.common.cinematic.state.CameraState;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Position, rotation, FOV, and roll animated independently as four {@link KeyframeTrack} channels,
 * composed into one {@link CameraState}. {@link #lookAt()}, when set, is resolved and applied by
 * {@code cinematic.client.ClientCutscenePlayer} — never here, since {@link #evaluate} must stay a
 * pure function of {@code (tick, partialTick)} and resolving a {@code LookAt} target needs a
 * {@code CutsceneContext} this track never sees. When {@link #lookAt()} is {@code null} (the
 * default), behavior is identical to before it existed — the rotation channel alone decides.
 */
public final class CameraTrack implements Track<CameraState> {

    private final KeyframeTrack<Vector3f> position;
    private final KeyframeTrack<Quaternionf> rotation;
    private final KeyframeTrack<Float> fov;
    private final KeyframeTrack<Float> roll;
    private final LookAt lookAt;

    public CameraTrack(KeyframeTrack<Vector3f> position, KeyframeTrack<Quaternionf> rotation,
                        KeyframeTrack<Float> fov, KeyframeTrack<Float> roll, LookAt lookAt) {
        this.position = position;
        this.rotation = rotation;
        this.fov = fov;
        this.roll = roll;
        this.lookAt = lookAt;
    }

    /** The optional look-at target for this shot's camera, or {@code null} if this shot only uses its own keyframed rotation. */
    public LookAt lookAt() {
        return lookAt;
    }

    @Override
    public CameraState evaluate(int tick, float partialTick) {
        return new CameraState(
                position.evaluate(tick, partialTick),
                rotation.evaluate(tick, partialTick),
                fov.evaluate(tick, partialTick),
                roll.evaluate(tick, partialTick));
    }
}
