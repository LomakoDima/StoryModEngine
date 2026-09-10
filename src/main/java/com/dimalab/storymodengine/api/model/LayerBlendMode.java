package com.dimalab.storymodengine.api.model;

/** How one animation layer combines with whatever pose the layers below it already produced. */
public enum LayerBlendMode {

    /**
     * Replaces the pose, blended toward it by the layer's weight — a full-body walk or idle. At
     * weight 1 the layer wins outright; at 0.5 the result is halfway between what was there and this
     * layer's own pose, which is how two clips cross-fade.
     */
    OVERRIDE,

    /**
     * Adds this layer's offset from rest on top of the existing pose — a recoil, a lean, a breathing
     * bob that should read on top of whatever the body is already doing rather than replacing it.
     * Only meaningful because clips are stored as deltas from bind pose (see {@code
     * gltf.GltfAnimationImporter}).
     */
    ADDITIVE
}
