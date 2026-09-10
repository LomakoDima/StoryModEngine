package com.dimalab.storymodengine.common.model;

/**
 * One primitive's vertex attributes, struct-of-arrays — {@code positions}/{@code normals} 3 floats
 * per vertex, {@code tangents} 4 (xyz + w handedness, per spec), {@code uv0}/{@code uv1} 2,
 * {@code joints}/{@code weights} 4 (glTF {@code JOINTS_0}/{@code WEIGHTS_0} only — see
 * MODEL_SYSTEM_DESIGN.md's non-goals for why higher joint sets aren't read). Any array beyond
 * {@code positions} may be empty (length 0) if the source primitive didn't declare that attribute;
 * {@link #hasNormals()}/{@link #hasTangents()}/{@link #hasUv()}/{@link #hasUv1()}/{@link
 * #hasSkin()} are the check.
 *
 * <p>{@code tangents} feeds real per-vertex {@code at_tangent} data on all three render paths: the
 * skinned ({@code GpuSkinBuffers}) and GPU-instanced ({@code InstanceBatchBuffers}) paths, which own
 * a persistent VAO and resolve the attribute location dynamically by name, and the CPU-immediate/
 * buffered path ({@code ModelRenderTypes#entityTriangles}), whose transient, vanilla-shared VAO has
 * no sanctioned hook of its own — that one goes through a Mixin bridge instead ({@code
 * BatchTangentCollector}/{@code BatchTangentBuffers}), fed from {@code ModelRenderer#emitVertex}. A
 * primitive with no tangent data still gets a placeholder fed on the buffered path (see {@code
 * BatchTangentCollector}'s own doc for why), so normal mapping there is genuinely supported now, not
 * merely degraded like a vanilla entity's.
 */
public record VertexData(float[] positions, float[] normals, float[] tangents, float[] uv0, float[] uv1,
                          int[] joints, float[] weights, int vertexCount) {

    public boolean hasNormals() {
        return normals.length > 0;
    }

    public boolean hasTangents() {
        return tangents.length > 0;
    }

    public boolean hasUv() {
        return uv0.length > 0;
    }

    public boolean hasUv1() {
        return uv1.length > 0;
    }

    public boolean hasSkin() {
        return joints.length > 0 && weights.length > 0;
    }
}
