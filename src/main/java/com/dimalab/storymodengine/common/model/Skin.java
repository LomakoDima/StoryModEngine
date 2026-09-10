package com.dimalab.storymodengine.common.model;

import org.joml.Matrix4f;

/**
 * A skin binds a mesh's vertices to a set of joints. {@code jointNodeIndices[i]} is the {@link
 * ModelNode#index()} of the node acting as joint {@code i} — and {@code i} is exactly what a vertex's
 * {@code JOINTS_0} attribute refers to. glTF only ever names joints by node index, so this array *is*
 * the mapping between "joint slot" and "node"; nothing downstream should assume the two coincide.
 *
 * <p>{@code inverseBindMatrices[i]} takes a vertex from model space into joint {@code i}'s local
 * space, per the glTF spec — see {@code client.model.CpuSkinner} for how the two combine.
 */
public record Skin(int[] jointNodeIndices, Matrix4f[] inverseBindMatrices) {

    public int jointCount() {
        return jointNodeIndices.length;
    }
}
