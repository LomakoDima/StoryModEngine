package com.dimalab.storymodengine.client.model;

import com.dimalab.storymodengine.common.model.Skin;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Map;

/**
 * Linear blend skinning — the formula, not the render path. {@link #computeSkinMatrices} is still
 * live: it turns the runtime joint hierarchy into the small per-joint matrix array that both the
 * (now GPU-based) render path and this class's own per-vertex methods need.
 *
 * <p>{@link #skinPosition}/{@link #skinNormal} are <b>no longer used to render anything</b> — see
 * {@code client.model.gpu.GpuSkinBuffers}, which does the actual per-vertex blend on the GPU via
 * transform feedback now. They stay, deliberately, as the reference implementation {@code
 * debug.ModelSelfTest} checks the GPU path's output against: the same formula computed two
 * different ways ought to agree, and disagreeing here is exactly the kind of bug a screenshot
 * wouldn't obviously reveal.
 *
 * <p>The skin matrix for joint {@code i} is {@code jointGlobal · inverseBind[i]}: the inverse bind
 * matrix takes a vertex from model space into the joint's rest-local space, and the joint's current
 * global matrix puts it back out into model space at wherever that joint is now.
 *
 * <p><b>The skinned mesh node's own transform is deliberately not applied</b> — glTF specifies that
 * it must be ignored, because a skinned vertex's placement is fully determined by its joints. Those
 * joints sit under the same {@code ModelSpace} correction root as everything else, so the facing/scale
 * correction still reaches skinned geometry through {@code jointGlobal}, not through the mesh node.
 */
public final class CpuSkinner {

    private CpuSkinner() {
    }

    /** One matrix per joint slot, in the order a vertex's {@code JOINTS_0} indices refer to. {@code out} is reused across frames; pass an array of the right length or null for a fresh one. */
    public static Matrix4f[] computeSkinMatrices(Skin skin, Map<Integer, RuntimeNode> nodes, Matrix4f[] out) {
        int count = skin.jointCount();
        if (out == null || out.length != count) {
            out = new Matrix4f[count];
            for (int i = 0; i < count; i++) {
                out[i] = new Matrix4f();
            }
        }
        for (int i = 0; i < count; i++) {
            RuntimeNode joint = nodes.get(skin.jointNodeIndices()[i]);
            if (joint == null) {
                out[i].identity();
                continue;
            }
            joint.globalMatrix().mul(skin.inverseBindMatrices()[i], out[i]);
        }
        return out;
    }

    /** Standard linear blend of up to 4 weighted joint transforms, writing into {@code dest}. Full affine transform — includes translation. */
    public static void skinPosition(Matrix4f[] skinMatrices, Vector3f bindPosition, int[] joints, float[] weights, int vertexIndex, Vector3f dest, Vector3f scratch) {
        dest.zero();
        for (int i = 0; i < 4; i++) {
            float weight = weights[vertexIndex * 4 + i];
            if (weight <= 0f) {
                continue;
            }
            int joint = joints[vertexIndex * 4 + i];
            if (joint < 0 || joint >= skinMatrices.length) {
                continue;
            }
            skinMatrices[joint].transformPosition(bindPosition, scratch);
            dest.add(scratch.mul(weight));
        }
    }

    /**
     * The same blend for a direction. Uses {@code transformDirection} (translation-free) and then
     * normalizes; the full inverse-transpose a non-uniformly-scaled rig would strictly require is
     * skipped, an accepted approximation for the entity-scale, near-uniform-scale rigs this loader
     * targets (stated in MODEL_SYSTEM_DESIGN.md rather than left implicit).
     */
    public static void skinNormal(Matrix4f[] skinMatrices, Vector3f bindNormal, int[] joints, float[] weights, int vertexIndex, Vector3f dest, Vector3f scratch) {
        dest.zero();
        for (int i = 0; i < 4; i++) {
            float weight = weights[vertexIndex * 4 + i];
            if (weight <= 0f) {
                continue;
            }
            int joint = joints[vertexIndex * 4 + i];
            if (joint < 0 || joint >= skinMatrices.length) {
                continue;
            }
            skinMatrices[joint].transformDirection(bindNormal, scratch);
            dest.add(scratch.mul(weight));
        }
        if (dest.lengthSquared() > 1.0e-12f) {
            dest.normalize();
        } else {
            dest.set(0f, 1f, 0f);
        }
    }
}
