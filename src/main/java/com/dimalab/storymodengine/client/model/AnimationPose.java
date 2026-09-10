package com.dimalab.storymodengine.client.model;

import com.dimalab.storymodengine.api.math.interp.Interpolators;
import com.dimalab.storymodengine.api.model.LayerBlendMode;
import com.dimalab.storymodengine.common.model.AnimationClip;
import com.dimalab.storymodengine.common.model.AnimationData;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * A sampled pose: what each animated bone's delta-from-rest is at one instant. This is the unit
 * animation blending works in — {@link #sample} produces one, {@link #mix} combines two, and {@link
 * #applyTo} writes the result onto a live {@link RuntimeNode} tree.
 *
 * <p>Blending only works because clips store deltas rather than absolute transforms (see {@code
 * gltf.GltfAnimationImporter}): mixing two deltas is meaningful, and a bone animated by only one of
 * the two clips correctly blends toward *identity* for the other rather than toward some arbitrary
 * absolute value.
 */
public final class AnimationPose {

    private final Map<Integer, BonePose> bones = new LinkedHashMap<>();

    public BonePose bone(int nodeIndex) {
        return bones.computeIfAbsent(nodeIndex, k -> new BonePose());
    }

    public BonePose get(int nodeIndex) {
        return bones.get(nodeIndex);
    }

    public boolean isEmpty() {
        return bones.isEmpty();
    }

    public void clear() {
        bones.clear();
    }

    /**
     * Samples {@code clip} at {@code time}. {@code boneMask}, when non-null, restricts sampling to
     * those node indices — that's how a layer animates only the upper body while a walk cycle drives
     * the legs underneath it.
     */
    public static AnimationPose sample(AnimationClip clip, float time, Set<Integer> boneMask) {
        AnimationPose pose = new AnimationPose();
        for (Map.Entry<Integer, AnimationData> entry : clip.nodes().entrySet()) {
            int nodeIndex = entry.getKey();
            if (boneMask != null && !boneMask.contains(nodeIndex)) {
                continue;
            }
            AnimationData data = entry.getValue();
            BonePose bone = pose.bone(nodeIndex);
            if (data.translation != null) {
                bone.translation = data.translation.sample(time);
            }
            if (data.rotation != null) {
                bone.rotation = data.rotation.sample(time);
            }
            if (data.scale != null) {
                bone.scale = data.scale.sample(time);
            }
        }
        return pose;
    }

    /**
     * Cross-fades two poses. A bone present in only one of them is blended against that channel's
     * identity delta, which is what makes a fade between clips that animate different bone sets look
     * right instead of popping.
     */
    public static AnimationPose mix(AnimationPose first, AnimationPose second, float factor) {
        float t = Math.min(1f, Math.max(0f, factor));
        AnimationPose result = new AnimationPose();

        Set<Integer> nodeIds = new LinkedHashSet<>(first.bones.keySet());
        nodeIds.addAll(second.bones.keySet());

        for (int nodeIndex : nodeIds) {
            BonePose a = first.get(nodeIndex);
            BonePose b = second.get(nodeIndex);
            BonePose mixed = result.bone(nodeIndex);

            Vector3f aT = a != null ? a.translation : null;
            Vector3f bT = b != null ? b.translation : null;
            if (aT != null || bT != null) {
                mixed.translation = Interpolators.VECTOR3F.interpolate(
                        aT != null ? aT : new Vector3f(), bT != null ? bT : new Vector3f(), t);
            }

            Quaternionf aR = a != null ? a.rotation : null;
            Quaternionf bR = b != null ? b.rotation : null;
            if (aR != null || bR != null) {
                mixed.rotation = Interpolators.QUATERNION_SLERP.interpolate(
                        aR != null ? aR : new Quaternionf(), bR != null ? bR : new Quaternionf(), t);
            }

            Vector3f aS = a != null ? a.scale : null;
            Vector3f bS = b != null ? b.scale : null;
            if (aS != null || bS != null) {
                mixed.scale = Interpolators.VECTOR3F.interpolate(
                        aS != null ? aS : new Vector3f(1f, 1f, 1f), bS != null ? bS : new Vector3f(1f, 1f, 1f), t);
            }
        }
        return result;
    }

    /**
     * Writes this pose onto live runtime nodes. Equivalent to {@link #applyTo(Map, LayerBlendMode,
     * float, AnimationPose)} with no reference pose — an additive layer then blends its delta against
     * identity, exactly as before this method gained a 4-arg overload. Kept so every pre-existing call
     * site keeps compiling unchanged.
     */
    public void applyTo(Map<Integer, RuntimeNode> nodes, LayerBlendMode blendMode, float weight) {
        applyTo(nodes, blendMode, weight, null);
    }

    /**
     * Writes this pose onto live runtime nodes.
     *
     * <p>{@link LayerBlendMode#OVERRIDE} reconstitutes the absolute target ({@code bind ⊕ delta}) and
     * moves the node's current pose toward it by {@code weight} — so weight 1 replaces, and anything
     * less cross-fades with whatever earlier layers left there. {@link LayerBlendMode#ADDITIVE} takes
     * the delta between this pose and {@code reference} (identity on any channel where {@code
     * reference} is null or doesn't touch that bone — reproducing the old always-against-identity
     * behavior exactly), scales that delta toward zero by {@code weight}, and composes it onto the
     * current pose, leaving the underlying animation intact.
     */
    public void applyTo(Map<Integer, RuntimeNode> nodes, LayerBlendMode blendMode, float weight, AnimationPose reference) {
        if (isEmpty() || weight <= 0f) {
            return;
        }
        float w = Math.min(1f, Math.max(0f, weight));

        for (Map.Entry<Integer, BonePose> entry : bones.entrySet()) {
            RuntimeNode node = nodes.get(entry.getKey());
            if (node == null) {
                continue;
            }
            BonePose pose = entry.getValue();
            if (blendMode == LayerBlendMode.OVERRIDE) {
                applyOverride(node, pose, w);
            } else {
                BonePose referenceBone = reference != null ? reference.get(entry.getKey()) : null;
                applyAdditive(node, pose, w, referenceBone);
            }
        }
    }

    private static void applyOverride(RuntimeNode node, BonePose pose, float weight) {
        if (pose.translation != null) {
            Vector3f target = node.definition().bindTranslation().add(pose.translation, new Vector3f());
            node.translation().lerp(target, weight);
        }
        if (pose.rotation != null) {
            Quaternionf target = new Quaternionf(node.definition().bindRotation()).mul(pose.rotation).normalize();
            node.rotation().slerp(target, weight).normalize();
        }
        if (pose.scale != null) {
            Vector3f target = node.definition().bindScale().mul(pose.scale, new Vector3f());
            node.scale().lerp(target, weight);
        }
    }

    /**
     * {@code reference} is the bone's own delta in some other pose to blend against instead of
     * identity — e.g. a clip's reference pose sampled at t=0 (see {@code
     * client.model.ClipLayer.referencePose}). When null (or when {@code reference} doesn't touch a
     * given channel), that channel's reference is identity — translation {@code (0,0,0)}, rotation
     * identity, scale {@code (1,1,1)} — which makes {@code delta == pose} exactly, reproducing the
     * pre-existing always-against-identity formula bit-for-bit.
     */
    private static void applyAdditive(RuntimeNode node, BonePose pose, float weight, BonePose reference) {
        if (pose.translation != null) {
            Vector3f referenceTranslation = reference != null && reference.translation != null
                    ? reference.translation : new Vector3f(0f, 0f, 0f);
            Vector3f delta = pose.translation.sub(referenceTranslation, new Vector3f());
            node.translation().add(delta.x * weight, delta.y * weight, delta.z * weight);
        }
        if (pose.rotation != null) {
            Quaternionf referenceRotation = reference != null && reference.rotation != null
                    ? reference.rotation : new Quaternionf();
            Quaternionf delta = new Quaternionf(referenceRotation).invert().mul(pose.rotation);
            Quaternionf scaledDelta = new Quaternionf().slerp(delta, weight).normalize();
            node.rotation().mul(scaledDelta).normalize();
        }
        if (pose.scale != null) {
            Vector3f referenceScale = reference != null && reference.scale != null
                    ? reference.scale : new Vector3f(1f, 1f, 1f);
            Vector3f deltaScale = new Vector3f(pose.scale.x / referenceScale.x, pose.scale.y / referenceScale.y, pose.scale.z / referenceScale.z);
            Vector3f scaledDelta = new Vector3f(1f, 1f, 1f).lerp(deltaScale, weight);
            node.scale().mul(scaledDelta);
        }
    }
}
