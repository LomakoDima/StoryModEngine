package com.dimalab.storymodengine.common.model;

import java.util.List;

/**
 * One glTF "primitive" — one draw call, one material. Triangle list only (glTF's default and only
 * mode this importer reads — see non-goals for TRIANGLE_STRIP/FAN/lines/points).
 *
 * <p>{@code morphTargets} is empty for the (overwhelming) common case of a mesh with none.
 */
public record Primitive(VertexData vertices, int[] indices, MaterialData material, List<MorphTarget> morphTargets) {

    private static final int VERTICES_PER_CUBE = 24;
    private static final int INDICES_PER_CUBE = 36;

    /**
     * "How many cube-equivalents' worth of vertex/index data this primitive contains" — a density
     * proxy that works for any glTF geometry, not just literal Blockbench cubes, using whichever of
     * the vertex-count or index-count estimate is larger. 24 vertices / 36 indices is exactly what one
     * unshared-quad cube (6 faces x 2 triangles, no shared vertices) produces. Ported from
     * HollowEngine's own {@code Primitive.cubeCount} (architecture reference only, not their source);
     * used by {@code ModelDefinition.renderPath()} (per-model draw-path choice) — an earlier
     * per-frame instancing threshold that also consulted this was removed once every {@code
     * InstanceBatchCollector} submission started drawing through {@code InstanceBatchBuffers}
     * regardless of count (see {@code InstanceFlush}'s own doc for why).
     */
    public int estimatedCubeCount() {
        int vertexEstimate = vertices.vertexCount() / VERTICES_PER_CUBE;
        int indexEstimate = indices.length / INDICES_PER_CUBE;
        return Math.max(vertexEstimate, indexEstimate);
    }

    /**
     * One morph target's per-vertex deltas from {@link #vertices}' bind pose — <b>deltas, not
     * absolute values</b>, per spec. {@code normalDeltas} may be empty if the target only declared
     * {@code POSITION}.
     */
    public record MorphTarget(float[] positionDeltas, float[] normalDeltas) {

        public boolean hasNormalDeltas() {
            return normalDeltas.length > 0;
        }
    }
}
