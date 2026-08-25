package com.dimalab.storymodengine.client.cinematic;

import com.dimalab.storymodengine.common.cinematic.state.CameraState;
import com.dimalab.storymodengine.common.logging.EngineLog;
import com.dimalab.storymodengine.api.math.Angles;
import com.dimalab.storymodengine.api.math.interp.Interpolators;
import com.dimalab.storymodengine.api.math.interp.TickValue;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.level.Level;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Overrides the client's render camera for the duration of a cutscene — the one class in this
 * whole system that actually touches Minecraft's concrete camera. There is no Forge event to
 * override camera *position* (only {@code ViewportEvent.ComputeCameraAngles}/{@code ComputeFov}
 * exist, angles/FOV/roll only — see {@code ARCHITECTURE.md}), so position/rotation instead go
 * through the same mechanism spectator camera-riding and every existing freecam-style mod already
 * uses: {@link Minecraft#setCameraEntity(Entity)} pointing the render camera at an {@link Entity}.
 * That entity here is a {@link Marker} — vanilla's own zero-size, do-nothing anchor entity —
 * created client-side only and **never added to the level** (never ticked, never rendered, never
 * seen by anything else), so nothing about a real gameplay entity is spent on it.
 *
 * <p>Because this rig entity is never ticked by the level, nothing manages its own previous/current
 * pose fields the way a normally-simulated entity's own {@code tick()} would — so this class does
 * that itself, explicitly, with this engine's own {@link TickValue} (one per channel: position,
 * rotation, FOV, roll), advanced once per client tick via {@link #tick} and sampled once per render
 * frame via {@link #renderFrame}/{@link #roll}, which snaps the marker's raw pose to the
 * smoothly-interpolated value for that frame — literally the example {@code TickValue}'s own
 * Javadoc names ("a cutscene camera's pose"). {@code roll} has no entity-level equivalent (no
 * vanilla entity ever rolls), so unlike position/rotation it's applied entirely through {@code
 * ComputeCameraAngles} by {@code ClientCutscenePlayer}, the same way FOV already goes through
 * {@code ComputeFov} instead of the marker.
 */
final class CameraRig {

    private final TickValue<Vector3f> position = TickValue.of(Interpolators.VECTOR3F, new Vector3f());
    private final TickValue<Quaternionf> rotation = TickValue.of(Interpolators.QUATERNION_SLERP, new Quaternionf());
    private final TickValue<Float> fov = TickValue.of(Interpolators.FLOAT, 70f);
    private final TickValue<Float> roll = TickValue.of(Interpolators.FLOAT, 0f);

    private Marker marker;
    private Entity previousCameraEntity;
    private Level level;
    private boolean active;

    void start(Level level) {
        Minecraft minecraft = Minecraft.getInstance();
        previousCameraEntity = minecraft.getCameraEntity();
        this.level = level;
        marker = new Marker(EntityType.MARKER, level);
        position.reset(new Vector3f());
        rotation.reset(new Quaternionf());
        fov.reset(70f);
        roll.reset(0f);
        active = true;
        minecraft.setCameraEntity(marker);
        EngineLog.channel("Cinematic").debug("Camera rig engaged");
    }

    void stop() {
        if (!active) {
            return;
        }
        active = false;
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setCameraEntity(previousCameraEntity != null ? previousCameraEntity : minecraft.player);
        marker = null;
        level = null;
        EngineLog.channel("Cinematic").debug("Camera rig released");
    }

    boolean isActive() {
        return active;
    }

    /** Feeds this tick's newly-evaluated camera pose into the smoothing {@link TickValue}s — call once per client tick. */
    void tick(CameraState state) {
        if (!active) {
            return;
        }
        position.tick(state.position());
        rotation.tick(state.rotation());
        fov.tick(state.fov());
        roll.tick(state.roll());
    }

    /**
     * Snaps the marker to this frame's interpolated pose and returns the FOV to apply — call once
     * per render frame, before camera setup. A {@code LookAt} target, if set, is already baked
     * into the rotation fed to {@link #tick} by {@code ClientCutscenePlayer} (computed once per
     * game tick, smoothed the same way every other channel is — not recomputed per render frame),
     * so this method never needs to know whether a frame's rotation came from keyframes or LookAt.
     * The position is passed through {@link CameraCollision} first, pulling it in toward the
     * viewing player if the authored path clips through solid geometry — see that class's Javadoc.
     */
    float renderFrame(float partialTick) {
        if (!active || marker == null) {
            return fov.current();
        }
        Vector3f pos = CameraCollision.correct(level, Minecraft.getInstance().player, position.get(partialTick));
        float[] yawPitch = Angles.toYawPitch(rotation.get(partialTick));
        marker.setPosRaw(pos.x, pos.y, pos.z);
        marker.setYRot(yawPitch[0]);
        marker.setXRot(yawPitch[1]);
        // The marker is never ticked by the level (deliberately — see the class Javadoc), so
        // nothing else ever advances its own previous-pose fields (xo/yo/zo, xRotO/yRotO).
        // Camera#setup still tries to interpolate from those toward the pose just set above, so
        // without this they'd stay frozen at wherever the marker was constructed — every frame
        // would then blend between that stale point and the real one, producing exactly the
        // rapid flicker/"stuck in a block" symptom this fixes. Syncing old==current here makes
        // that (redundant) vanilla interpolation a no-op; the smoothing already happened above,
        // via this engine's own TickValue.
        marker.setOldPosAndRot();
        return fov.get(partialTick);
    }

    /** This frame's interpolated camera roll, in degrees — sampled separately since it's applied via {@code ComputeCameraAngles}, not the marker. */
    float roll(float partialTick) {
        return active ? roll.get(partialTick) : 0f;
    }
}
