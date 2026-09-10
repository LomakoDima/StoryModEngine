package com.dimalab.storymodengine.client.model;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * One bone's sampled animation values for a frame, each nullable — a clip that only rotates a bone
 * leaves translation and scale null, and the bone keeps its bind value for those rather than being
 * snapped to zero.
 *
 * <p>These are <b>deltas from the bind pose</b>, matching how clips are stored — see {@code
 * gltf.GltfAnimationImporter}. Identity for a delta is {@code (0,0,0)} translation, identity
 * quaternion, {@code (1,1,1)} scale.
 */
public final class BonePose {

    public Vector3f translation;
    public Quaternionf rotation;
    public Vector3f scale;

    public boolean isEmpty() {
        return translation == null && rotation == null && scale == null;
    }
}
