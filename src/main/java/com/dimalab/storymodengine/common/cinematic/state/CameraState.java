package com.dimalab.storymodengine.common.cinematic.state;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The camera pose a {@code CameraTrack} evaluates to at one instant — plain data, independent of
 * Minecraft's own concrete camera implementation (no {@code Camera}, no {@code GameRenderer}
 * reference here). The {@code cinematic.client} integration layer is the only place this gets
 * applied to a real client camera — see {@code ARCHITECTURE.md}'s "why the core stays Forge-free".
 *
 * <p>{@code roll} (degrees) has no {@code Entity}-level equivalent the way position/rotation do
 * (no vanilla entity ever rolls) — {@code cinematic.client.ClientCutscenePlayer} applies it
 * through {@code ViewportEvent.ComputeCameraAngles} instead of the camera rig's marker entity, the
 * same reason {@code fov} already goes through its own separate {@code ComputeFov} hook rather
 * than the entity.
 */
public record CameraState(Vector3f position, Quaternionf rotation, float fov, float roll) {
}
