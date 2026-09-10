package com.dimalab.storymodengine.common.model;

import com.dimalab.storymodengine.common.model.track.QuatTrack;
import com.dimalab.storymodengine.common.model.track.Vec3Track;

/**
 * Everything one animation clip does to one node: up to three tracks, one per TRS component, each
 * nullable (a clip that only rotates a bone has translation/scale null, and the bone keeps its bind
 * value for those). Three typed fields rather than a list of channels the sampler has to filter —
 * sampling a node is three null checks, not a scan.
 */
public final class AnimationData {

    public Vec3Track translation;
    public QuatTrack rotation;
    public Vec3Track scale;

    public float duration() {
        float max = 0f;
        if (translation != null) {
            max = Math.max(max, translation.duration());
        }
        if (rotation != null) {
            max = Math.max(max, rotation.duration());
        }
        if (scale != null) {
            max = Math.max(max, scale.duration());
        }
        return max;
    }
}
