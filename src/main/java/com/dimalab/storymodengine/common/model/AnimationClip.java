package com.dimalab.storymodengine.common.model;

import java.util.Map;

/**
 * One named animation, indexed by the node it drives — {@code Map<nodeIndex, AnimationData>} rather
 * than a flat channel list, so posing walks exactly the nodes this clip actually animates and each
 * one's three tracks are already grouped.
 */
public record AnimationClip(String name, Map<Integer, AnimationData> nodes, float duration) {

    public static AnimationClip of(String name, Map<Integer, AnimationData> nodes) {
        float duration = 0f;
        for (AnimationData data : nodes.values()) {
            duration = Math.max(duration, data.duration());
        }
        return new AnimationClip(name, nodes, duration);
    }
}
