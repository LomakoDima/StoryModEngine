package com.dimalab.storymodengine.common.model.gltf;

import com.dimalab.storymodengine.common.logging.EngineLog;
import com.dimalab.storymodengine.common.model.AnimationClip;
import com.dimalab.storymodengine.common.model.AnimationData;
import com.dimalab.storymodengine.common.model.ModelNode;
import com.dimalab.storymodengine.common.model.track.QuatTrack;
import com.dimalab.storymodengine.common.model.track.TrackInterpolation;
import com.dimalab.storymodengine.common.model.track.Vec3Track;
import net.minecraft.resources.ResourceLocation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * animations -> {@link AnimationClip}.
 *
 * <p><b>The central decision here: keyframe values are converted to deltas relative to the target
 * node's bind pose, not stored as the absolute transforms glTF carries.</b> A glTF rotation track
 * says "at t, this bone's local rotation *is* Q"; what gets stored is "at t, this bone is rotated by
 * {@code bindRotation⁻¹ · Q} away from its rest pose". The absolute value is trivially recovered
 * ({@code bindRotation · delta}), so nothing is lost — but the delta form is what makes blending
 * possible at all:
 *
 * <ul>
 *   <li>two clips can be mixed by interpolating their deltas, then applying the result once;</li>
 *   <li>a clip can be applied at partial weight by scaling its delta toward identity;</li>
 *   <li>an additive layer (a recoil, a lean) can stack on top of whatever pose is already there,
 *       because a delta is meaningful independently of what it's applied to.</li>
 * </ul>
 *
 * With absolute values none of that works — applying a clip can only overwrite, so a second clip
 * always wins outright. See {@code client.model.AnimationPose} for the consuming side.
 */
public final class GltfAnimationImporter {

    private final GltfDocument document;
    private final GltfAccessorReader accessors;
    private final Map<Integer, ModelNode> nodesByIndex;
    private final ResourceLocation source;

    public GltfAnimationImporter(GltfDocument document, GltfAccessorReader accessors, Map<Integer, ModelNode> nodesByIndex, ResourceLocation source) {
        this.document = document;
        this.accessors = accessors;
        this.nodesByIndex = nodesByIndex;
        this.source = source;
    }

    public List<AnimationClip> importAll() {
        List<AnimationClip> clips = new ArrayList<>();
        if (document.animations == null) {
            return clips;
        }
        for (int i = 0; i < document.animations.size(); i++) {
            GltfDocument.GltfAnimation raw = document.animations.get(i);
            if (raw.channels == null || raw.samplers == null) {
                continue;
            }
            String name = raw.name != null ? raw.name : ("animation_" + i);
            Map<Integer, AnimationData> nodes = importChannels(raw, name);
            if (!nodes.isEmpty()) {
                clips.add(AnimationClip.of(name, nodes));
            }
        }
        return clips;
    }

    private Map<Integer, AnimationData> importChannels(GltfDocument.GltfAnimation raw, String clipName) {
        Map<Integer, AnimationData> byNode = new LinkedHashMap<>();
        for (GltfDocument.GltfAnimationChannel channel : raw.channels) {
            // Some exporters emit channels with no target node at all — skip rather than fail the clip.
            if (channel.target == null || channel.target.node == null || channel.target.node < 0) {
                continue;
            }
            ModelNode node = nodesByIndex.get(channel.target.node);
            if (node == null) {
                EngineLog.channel("Model").debug("{}: animation '{}' targets node {}, which isn't in the imported hierarchy — skipped", source, clipName, channel.target.node);
                continue;
            }

            GltfDocument.GltfAnimationSampler sampler = raw.samplers.get(channel.sampler);
            float[] times = accessors.readFloats(sampler.input);
            if (times.length == 0) {
                continue;
            }

            boolean cubicSpline = "CUBICSPLINE".equals(sampler.interpolation);
            boolean step = "STEP".equals(sampler.interpolation);
            float[] values = accessors.readFloats(sampler.output);

            AnimationData data = byNode.computeIfAbsent(node.index(), k -> new AnimationData());
            switch (channel.target.path) {
                case "translation" -> data.translation = translationTrack(values, times, cubicSpline, step, node);
                case "rotation" -> data.rotation = rotationTrack(values, times, cubicSpline, step, node);
                case "scale" -> data.scale = scaleTrack(values, times, cubicSpline, step, node);
                default -> {
                    // "weights" (morph targets) — deliberately out of scope, see MODEL_SYSTEM_DESIGN.md.
                }
            }
        }
        return byNode;
    }

    /**
     * CUBICSPLINE stores three values per keyframe (in-tangent, value, out-tangent) instead of one —
     * {@link #offset} finds the middle (value) slot; the cubic-spline branches below additionally
     * read the slots on either side of it directly, at {@code keyframeBase} and {@code keyframeBase +
     * 2 * componentCount}.
     */
    private static int stride(int componentCount, boolean cubicSpline) {
        return cubicSpline ? componentCount * 3 : componentCount;
    }

    private static int offset(int componentCount, boolean cubicSpline) {
        return cubicSpline ? componentCount : 0;
    }

    private Vec3Track translationTrack(float[] values, float[] times, boolean cubicSpline, boolean step, ModelNode node) {
        int count = times.length;
        int stride = stride(3, cubicSpline);
        int offset = offset(3, cubicSpline);
        Vector3f bind = node.bindTranslation();
        Vector3f[] deltas = new Vector3f[count];
        Vector3f[] inTangents = cubicSpline ? new Vector3f[count] : null;
        Vector3f[] outTangents = cubicSpline ? new Vector3f[count] : null;
        for (int i = 0; i < count; i++) {
            int base = i * stride + offset;
            deltas[i] = new Vector3f(values[base] - bind.x, values[base + 1] - bind.y, values[base + 2] - bind.z);
            if (cubicSpline) {
                int keyframeBase = i * stride;
                // Translation delta = value - bind (additive); bind is constant, so its derivative is
                // 0 — a tangent (a rate of change) needs no bind adjustment at all, just a plain copy.
                inTangents[i] = new Vector3f(values[keyframeBase], values[keyframeBase + 1], values[keyframeBase + 2]);
                int outBase = keyframeBase + 2 * 3;
                outTangents[i] = new Vector3f(values[outBase], values[outBase + 1], values[outBase + 2]);
            }
        }
        return cubicSpline
                ? new Vec3Track(times, deltas, inTangents, outTangents, TrackInterpolation.CUBICSPLINE)
                : new Vec3Track(times, deltas, step);
    }

    private QuatTrack rotationTrack(float[] values, float[] times, boolean cubicSpline, boolean step, ModelNode node) {
        int count = times.length;
        int stride = stride(4, cubicSpline);
        int offset = offset(4, cubicSpline);
        Quaternionf bindInverse = new Quaternionf(node.bindRotation()).invert();
        Quaternionf[] deltas = new Quaternionf[count];
        Quaternionf[] inTangents = cubicSpline ? new Quaternionf[count] : null;
        Quaternionf[] outTangents = cubicSpline ? new Quaternionf[count] : null;
        for (int i = 0; i < count; i++) {
            int base = i * stride + offset;
            Quaternionf absolute = new Quaternionf(values[base], values[base + 1], values[base + 2], values[base + 3]);
            deltas[i] = new Quaternionf(bindInverse).mul(absolute).normalize();
            if (cubicSpline) {
                int keyframeBase = i * stride;
                // Rotation delta is bindRotation⁻¹·value — left-multiplying by a constant quaternion
                // is LINEAR in the operand's own (x,y,z,w) (it's literally a 4x4 matrix acting on
                // them), so the derivative transforms the same way: bindRotation⁻¹·tangent. Not
                // normalized, unlike the value above — a tangent isn't meant to be unit-length;
                // normalizing it would corrupt the rate of change the spline formula actually needs.
                Quaternionf inRaw = new Quaternionf(values[keyframeBase], values[keyframeBase + 1], values[keyframeBase + 2], values[keyframeBase + 3]);
                inTangents[i] = new Quaternionf(bindInverse).mul(inRaw);
                int outBase = keyframeBase + 2 * 4;
                Quaternionf outRaw = new Quaternionf(values[outBase], values[outBase + 1], values[outBase + 2], values[outBase + 3]);
                outTangents[i] = new Quaternionf(bindInverse).mul(outRaw);
            }
        }
        return cubicSpline
                ? new QuatTrack(times, deltas, inTangents, outTangents, TrackInterpolation.CUBICSPLINE)
                : new QuatTrack(times, deltas, step);
    }

    private Vec3Track scaleTrack(float[] values, float[] times, boolean cubicSpline, boolean step, ModelNode node) {
        int count = times.length;
        int stride = stride(3, cubicSpline);
        int offset = offset(3, cubicSpline);
        Vector3f bind = node.bindScale();
        Vector3f[] deltas = new Vector3f[count];
        Vector3f[] inTangents = cubicSpline ? new Vector3f[count] : null;
        Vector3f[] outTangents = cubicSpline ? new Vector3f[count] : null;
        for (int i = 0; i < count; i++) {
            int base = i * stride + offset;
            deltas[i] = new Vector3f(safeDivide(values[base], bind.x), safeDivide(values[base + 1], bind.y), safeDivide(values[base + 2], bind.z));
            if (cubicSpline) {
                int keyframeBase = i * stride;
                // Scale delta is value/bind (multiplicative); bind is constant, so d(value/bind)/dt =
                // tangent/bind — the same safeDivide treatment the value itself already gets above.
                inTangents[i] = new Vector3f(safeDivide(values[keyframeBase], bind.x), safeDivide(values[keyframeBase + 1], bind.y), safeDivide(values[keyframeBase + 2], bind.z));
                int outBase = keyframeBase + 2 * 3;
                outTangents[i] = new Vector3f(safeDivide(values[outBase], bind.x), safeDivide(values[outBase + 1], bind.y), safeDivide(values[outBase + 2], bind.z));
            }
        }
        return cubicSpline
                ? new Vec3Track(times, deltas, inTangents, outTangents, TrackInterpolation.CUBICSPLINE)
                : new Vec3Track(times, deltas, step);
    }

    /** A zero bind scale would make the delta infinite; a degenerate rest scale is a broken rig, so treat the delta as "no change" rather than poisoning the track with NaN. */
    private static float safeDivide(float value, float bind) {
        return Math.abs(bind) < 1.0e-6f ? 1f : value / bind;
    }
}
