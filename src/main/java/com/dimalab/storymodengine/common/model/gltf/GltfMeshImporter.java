package com.dimalab.storymodengine.common.model.gltf;

import com.dimalab.storymodengine.api.model.ModelFormatException;
import com.dimalab.storymodengine.common.logging.EngineLog;
import com.dimalab.storymodengine.common.model.GeometryUtils;
import com.dimalab.storymodengine.common.model.MaterialData;
import com.dimalab.storymodengine.common.model.Mesh;
import com.dimalab.storymodengine.common.model.Primitive;
import com.dimalab.storymodengine.common.model.Skin;
import com.dimalab.storymodengine.common.model.VertexData;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** meshes -> Mesh/Primitive/VertexData. One glTF "primitive" is one draw call with one material — matches this importer's {@link Primitive} 1:1, no merging or splitting. */
public final class GltfMeshImporter {

    private static final int MODE_TRIANGLES = 4;

    private final GltfDocument document;
    private final GltfAccessorReader accessors;
    private final List<MaterialData> materials;
    private final ResourceLocation source;

    public GltfMeshImporter(GltfDocument document, GltfAccessorReader accessors, List<MaterialData> materials, ResourceLocation source) {
        this.document = document;
        this.accessors = accessors;
        this.materials = materials;
        this.source = source;
    }

    public Mesh importMesh(int meshIndex, Skin skin) {
        GltfDocument.GltfMesh raw = document.meshes.get(meshIndex);
        if (raw.primitives == null) {
            throw new ModelFormatException(source, "mesh '" + (raw.name != null ? raw.name : meshIndex) + "' has no primitives");
        }
        List<Primitive> primitives = new ArrayList<>();
        for (GltfDocument.GltfPrimitive rawPrimitive : raw.primitives) {
            Primitive primitive = importPrimitive(rawPrimitive, raw.name, skin);
            if (primitive != null) {
                primitives.add(primitive);
            }
        }
        return new Mesh(raw.name, primitives);
    }

    private Primitive importPrimitive(GltfDocument.GltfPrimitive raw, String meshName, Skin skin) {
        // Checked, not assumed: mode 4 (TRIANGLES) is glTF's default and the only topology this
        // renderer emits. Silently drawing a strip/fan/line primitive as a triangle list would
        // produce scrambled geometry rather than an obvious failure.
        int mode = raw.mode == null ? MODE_TRIANGLES : raw.mode;
        if (mode != MODE_TRIANGLES) {
            EngineLog.channel("Model").warn("{}: skipping primitive with unsupported mode {} (only TRIANGLES is supported)", source, mode);
            return null;
        }

        if (raw.attributes == null) {
            throw new ModelFormatException(source, "primitive in mesh '" + meshName + "' has no attributes");
        }
        GltfDocument.GltfAttributes attrs = raw.attributes;
        float[] positions = attrs.POSITION != null ? accessors.readFloats(attrs.POSITION) : new float[0];
        float[] uv0 = attrs.TEXCOORD_0 != null ? accessors.readFloats(attrs.TEXCOORD_0) : new float[0];
        float[] uv1 = attrs.TEXCOORD_1 != null ? accessors.readFloats(attrs.TEXCOORD_1) : new float[0];
        int[] joints = attrs.JOINTS_0 != null ? accessors.readInts(attrs.JOINTS_0) : new int[0];
        float[] weights = attrs.WEIGHTS_0 != null ? accessors.readFloats(attrs.WEIGHTS_0) : new float[0];
        int vertexCount = positions.length / 3;

        // Every attribute above is read from its own, independent accessor — nothing about glTF's
        // format guarantees their declared counts agree with each other. A mismatch left unchecked
        // here surfaces later as an ArrayIndexOutOfBoundsException deep inside GeometryUtils or
        // GpuSkinBuffers, on whichever attribute happens to be shorter than vertexCount expects, with
        // no indication of which file field actually caused it.
        requireVertexAligned("TEXCOORD_0", uv0.length, 2, vertexCount);
        requireVertexAligned("TEXCOORD_1", uv1.length, 2, vertexCount);
        requireVertexAligned("JOINTS_0", joints.length, 4, vertexCount);
        requireVertexAligned("WEIGHTS_0", weights.length, 4, vertexCount);

        // The GPU skin path (gltf_skin_morph.vsh) has none of CpuSkinner's own bounds-checking: an
        // out-of-range JOINTS_0 index reaches texelFetch undefined, and an all-zero WEIGHTS_0 vertex
        // divides by a zero homogeneous w. Sanitized here, once, for every skinned primitive — see
        // GeometryUtils.sanitizeSkinning's own doc. Skipped for an unskinned node (ModelRenderer only
        // takes the skinned render branch when the node actually has a Skin), where stray JOINTS_0/
        // WEIGHTS_0 data, if present at all, is never fed to a shader anyway.
        if (skin != null && joints.length > 0 && weights.length > 0) {
            GeometryUtils.sanitizeSkinning(joints, weights, skin.jointCount());
        }

        int[] indices = raw.indices != null ? accessors.readInts(raw.indices) : identityIndices(vertexCount);
        for (int index : indices) {
            if (index < 0 || index >= vertexCount) {
                throw new ModelFormatException(source, "index " + index + " is out of range for "
                        + vertexCount + " vertices in mesh '" + meshName + "'");
            }
        }

        // NORMAL is optional in glTF, and the spec's own fallback is flat face normals — not the
        // constant (0,1,0) an earlier version substituted, which lit every such model like a floor.
        float[] normals;
        if (attrs.NORMAL != null) {
            normals = accessors.readFloats(attrs.NORMAL);
            requireVertexAligned("NORMAL", normals.length, 3, vertexCount);
        } else if (vertexCount > 0) {
            normals = GeometryUtils.recalculateNormals(positions, indices);
        } else {
            normals = new float[0];
        }

        // TANGENT is likewise optional; unlike NORMAL the spec has no mandated fallback, but a
        // computed one is still better than none for whatever eventually consumes it (see
        // VertexData's own doc — GpuSkinBuffers/InstanceBatchBuffers now do), so this recomputes
        // under the same condition HE's importer does: positions, normals and a UV set to derive a
        // direction from.
        //
        // A file-provided accessor is NOT trusted as-is. A real converted rig (this engine's own
        // player-model NPC) shipped TANGENT data with degenerate (near-zero-length) entries for SOME
        // vertices, correct UV-derived ones for the rest. An earlier version patched only the bad
        // vertices in place (GeometryUtils.sanitizeTangents' own arbitrary-axis-relative-to-normal
        // fallback) — that's a real, silent seam: the patched vertex's tangent has no relationship to
        // its neighbors' real, UV-aligned ones. Invisible under ordinary one-bounce tangent-space
        // normal mapping, but glaring under BSL's own iterative parallax ray-march (Advanced
        // Materials' PARALLAX option specifically, confirmed by the user narrowing it down past
        // Advanced Materials as a whole), which steps through texture space using the interpolated
        // tangent basis and needs it to be locally consistent, not just individually valid. So: if
        // ANY vertex in the file's own TANGENT is degenerate, the WHOLE array is discarded in favor of
        // recalculateTangents below — the same UV-consistent algorithm used when TANGENT is absent
        // entirely — rather than trusting a mix of file data and unrelated per-vertex patches. Only a
        // fully-valid file TANGENT is used as-is (through sanitizeTangents' now-lighter safety net —
        // see its own doc).
        float[] tangents;
        if (attrs.TANGENT != null) {
            float[] rawTangents = accessors.readFloats(attrs.TANGENT);
            requireVertexAligned("TANGENT", rawTangents.length, 4, vertexCount);
            if (GeometryUtils.hasDegenerateTangent(rawTangents) && vertexCount > 0 && normals.length > 0 && uv0.length > 0) {
                EngineLog.channel("Model").warn("{}: mesh '{}' TANGENT accessor has degenerate entries — "
                                + "discarding it entirely and recomputing from UVs for mesh-wide consistency",
                        source, meshName);
                tangents = GeometryUtils.recalculateTangents(positions, normals, uv0, indices);
            } else {
                tangents = GeometryUtils.sanitizeTangents(rawTangents, normals);
            }
        } else if (vertexCount > 0 && normals.length > 0 && uv0.length > 0) {
            tangents = GeometryUtils.recalculateTangents(positions, normals, uv0, indices);
        } else {
            tangents = new float[0];
        }

        MaterialData material = raw.material != null && raw.material < materials.size()
                ? materials.get(raw.material)
                : MaterialData.untextured();

        List<Primitive.MorphTarget> morphTargets = importMorphTargets(raw, vertexCount);

        return new Primitive(new VertexData(positions, normals, tangents, uv0, uv1, joints, weights, vertexCount),
                indices, material, morphTargets);
    }

    /**
     * Throws when {@code array} came from a real, present accessor (length {@code &gt; 0}) whose
     * declared count disagrees with {@code vertexCount} — glTF gives no guarantee that independently-
     * declared accessors agree with each other, and a silent mismatch here surfaces much later as an
     * {@code ArrayIndexOutOfBoundsException} deep inside {@code GeometryUtils} or {@code
     * GpuSkinBuffers} with no indication of which file field actually caused it. Length {@code 0} is
     * not an error — it's this importer's own "attribute absent" sentinel, already handled by the
     * recompute-fallback logic around each call site.
     */
    private void requireVertexAligned(String attrName, int arrayLength, int componentsPerVertex, int vertexCount) {
        int expected = vertexCount * componentsPerVertex;
        if (arrayLength > 0 && arrayLength != expected) {
            throw new ModelFormatException(source, attrName + " declares " + (arrayLength / componentsPerVertex)
                    + " vertices but POSITION declares " + vertexCount);
        }
    }

    /** Each target maps {@code POSITION}/{@code NORMAL} to the accessor holding that attribute's per-vertex delta from the primitive's own bind pose — not absolute values, per spec. */
    private List<Primitive.MorphTarget> importMorphTargets(GltfDocument.GltfPrimitive raw, int vertexCount) {
        if (raw.targets == null || raw.targets.isEmpty()) {
            return List.of();
        }
        List<Primitive.MorphTarget> targets = new ArrayList<>(raw.targets.size());
        for (Map<String, Integer> target : raw.targets) {
            Integer positionAccessor = target.get("POSITION");
            Integer normalAccessor = target.get("NORMAL");
            float[] positionDeltas = positionAccessor != null ? accessors.readFloats(positionAccessor) : new float[0];
            float[] normalDeltas = normalAccessor != null ? accessors.readFloats(normalAccessor) : new float[0];
            requireVertexAligned("morph target POSITION", positionDeltas.length, 3, vertexCount);
            requireVertexAligned("morph target NORMAL", normalDeltas.length, 3, vertexCount);
            targets.add(new Primitive.MorphTarget(positionDeltas, normalDeltas));
        }
        return targets;
    }

    private static int[] identityIndices(int vertexCount) {
        int[] indices = new int[vertexCount];
        for (int i = 0; i < vertexCount; i++) {
            indices[i] = i;
        }
        return indices;
    }
}
