package com.dimalab.storymodengine.client.model.debug;

import com.dimalab.storymodengine.api.model.LayerBlendMode;
import com.dimalab.storymodengine.api.model.ModelFormatException;
import com.dimalab.storymodengine.client.model.AnimationPose;
import com.dimalab.storymodengine.client.model.CpuSkinner;
import com.dimalab.storymodengine.client.model.IrisCompat;
import com.dimalab.storymodengine.client.model.ModelInstance;
import com.dimalab.storymodengine.client.model.ModelRenderTypes;
import com.dimalab.storymodengine.client.model.ModelTexture;
import com.dimalab.storymodengine.client.model.RenderFrameClock;
import com.dimalab.storymodengine.client.model.pbr.LabPbrConverter;
import com.dimalab.storymodengine.client.model.gpu.GpuSkinBuffers;
import com.dimalab.storymodengine.client.model.gpu.InstanceBatchCollector;
import com.dimalab.storymodengine.client.model.animator.expr.AnimEvalContext;
import com.dimalab.storymodengine.client.model.animator.expr.AnimExpr;
import com.dimalab.storymodengine.client.model.animator.expr.AnimationExpression;
import com.dimalab.storymodengine.client.model.animator.expr.AnimationVectorExpression;
import com.dimalab.storymodengine.client.model.animator.ProceduralBoneTransformSpec;
import com.dimalab.storymodengine.client.model.animator.ProceduralLayer;
import com.dimalab.storymodengine.client.model.animator.ProceduralLayerSpec;
import com.dimalab.storymodengine.client.model.animator.AnimationController;
import com.dimalab.storymodengine.client.model.animator.AnimationControllerLayerSpec;
import com.dimalab.storymodengine.client.model.animator.AnimationControllerStateSpec;
import com.dimalab.storymodengine.client.model.animator.AnimationControllerTransitionSpec;
import com.dimalab.storymodengine.client.model.animator.AnimationPlayMode;
import com.dimalab.storymodengine.client.model.animator.AnimatorLayerSpec;
import com.dimalab.storymodengine.client.model.animator.AnimatorPresets;
import com.dimalab.storymodengine.client.model.animator.BoneMask;
import com.dimalab.storymodengine.client.model.animator.ClipAnimationLayerSpec;
import com.dimalab.storymodengine.client.model.animator.ClipLayer;
import com.dimalab.storymodengine.client.model.animator.ClipPlayback;
import com.dimalab.storymodengine.client.model.animator.LayerPose;
import com.dimalab.storymodengine.client.model.animator.PoseTarget;
import com.dimalab.storymodengine.client.model.animator.StandardPlayerAnimatorPreset;
import com.dimalab.storymodengine.client.model.RuntimeNode;
import com.dimalab.storymodengine.common.model.AlphaMode;
import com.dimalab.storymodengine.common.model.AnimationClip;
import com.dimalab.storymodengine.common.model.AnimationData;
import com.dimalab.storymodengine.common.model.GeometryUtils;
import com.dimalab.storymodengine.common.model.Mesh;
import com.dimalab.storymodengine.common.model.ModelDefinition;
import com.dimalab.storymodengine.common.model.ModelNode;
import com.dimalab.storymodengine.common.model.ModelSpace;
import com.dimalab.storymodengine.common.model.Primitive;
import com.dimalab.storymodengine.common.model.RenderPath;
import com.dimalab.storymodengine.common.model.Skin;
import com.dimalab.storymodengine.common.model.gltf.ExternalModelIO;
import com.dimalab.storymodengine.common.model.gltf.GlbContainer;
import com.dimalab.storymodengine.common.model.gltf.GltfDocument;
import com.dimalab.storymodengine.common.model.gltf.GltfMaterialImporter;
import com.dimalab.storymodengine.common.model.gltf.GltfModelParser;
import com.dimalab.storymodengine.common.model.MaterialData;
import com.dimalab.storymodengine.common.model.ModelLoading;
import com.dimalab.storymodengine.common.model.ModelMetadata;
import com.dimalab.storymodengine.common.model.ModelMetadataLoader;
import com.dimalab.storymodengine.common.model.VertexData;
import com.dimalab.storymodengine.common.model.physics.ModelRotation;
import com.dimalab.storymodengine.common.model.rig.ModelRig;
import com.dimalab.storymodengine.common.model.rig.RigBone;
import net.minecraft.core.Direction;
import com.dimalab.storymodengine.common.model.physics.ModelBounds;
import com.dimalab.storymodengine.common.model.physics.ModelVoxelizer;
import com.dimalab.storymodengine.common.model.track.QuatTrack;
import com.dimalab.storymodengine.common.model.track.TrackInterpolation;
import com.dimalab.storymodengine.common.model.track.Vec3Track;
import com.dimalab.storymodengine.common.voxel.ShapeDefinition;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This project's established in-game JUnit substitute (no test framework is configured — same
 * reasoning as {@code quest.debug.QuestCompilerTestCommand}/{@code raycast.debug.RaycastSelfTestCommand}):
 * hand-built inputs run through the real parser and posing code, asserted against known-correct
 * results. Every check is pure-JVM — no world, no player, no GL context.
 *
 * <p>The checks deliberately target the invariants that are easy to get quietly wrong: the node
 * hierarchy surviving import, {@code matrix} decomposition, the facing correction, animation values
 * being stored as deltas, and pose blending actually blending.
 */
public final class ModelSelfTest {

    private ModelSelfTest() {
    }

    public record Result(String name, boolean passed, String detail) {
    }

    public static List<Result> runAll() {
        List<Result> results = new ArrayList<>();
        results.add(run("glb container round-trip", ModelSelfTest::testGlbRoundTrip));
        results.add(run("minimal gltf triangle parse", ModelSelfTest::testTriangleParse));
        results.add(run("sparse accessor overrides applied", ModelSelfTest::testSparseAccessor));
        results.add(run("sparse accessor without a base bufferView", ModelSelfTest::testSparseAccessorWithoutBase));
        results.add(run("external model folder rejects path traversal", ModelSelfTest::testExternalModelIoRejectsTraversal));
        results.add(run("node hierarchy preserved", ModelSelfTest::testHierarchyPreserved));
        results.add(run("node matrix decomposition", ModelSelfTest::testMatrixDecomposition));
        results.add(run("blockbench facing detection", ModelSelfTest::testFacingDetection));
        results.add(run("animation stored as bind-pose deltas", ModelSelfTest::testAnimationDeltas));
        results.add(run("pose blending (override at half weight)", ModelSelfTest::testPoseBlending));
        results.add(run("additive layering", ModelSelfTest::testAdditiveLayering));
        results.add(run("skin + animation math", ModelSelfTest::testSkinningMath));
        results.add(run("GPU skinning matches the CPU formula", ModelSelfTest::testGpuSkinningMatchesCpu));
        results.add(run("model texture generates a real mip chain", ModelSelfTest::testModelTextureGeneratesMipmaps));
        results.add(run("Iris/Oculus soft dependency fails safe when absent", ModelSelfTest::testIrisCompatFailsSafeWhenAbsent));
        results.add(run("CUBICSPLINE track matches the glTF Hermite formula", ModelSelfTest::testCubicSplineTrackMatchesHermiteFormula));
        results.add(run("CUBICSPLINE quaternion track stays normalized", ModelSelfTest::testCubicSplineQuatTrackNormalizesResult));
        results.add(run("instanced submission combines node and pose transforms", ModelSelfTest::testInstancedSubmissionCombinesNodeAndPose));
        results.add(run("render path heuristic picks batching/pipeline per model", ModelSelfTest::testRenderPathHeuristic));
        results.add(run("triangles emitted as triangles", ModelSelfTest::testTriangleTopology));
        results.add(run("tangents recomputed from UVs when absent", ModelSelfTest::testTangentRecompute));
        results.add(run("sanitizeTangents repairs degenerate raw tangent data", ModelSelfTest::testSanitizeTangentsDegenerateInput));
        results.add(run("importer rejects cross-attribute length mismatch", ModelSelfTest::testGltfMeshImporterRejectsCrossAttributeLengthMismatch));
        results.add(run("importer rejects out-of-range indices", ModelSelfTest::testGltfMeshImporterRejectsOutOfRangeIndices));
        results.add(run("importer rejects a skin with no joints array", ModelSelfTest::testImportSkinsRejectsMissingJoints));
        results.add(run("importer rejects mismatched inverse bind matrix count", ModelSelfTest::testImportSkinsRejectsMismatchedInverseBindCount));
        results.add(run("sanitizeSkinning clamps joints and normalizes weights", ModelSelfTest::testSanitizeSkinningClampsOutOfRangeJointsAndNormalizesWeights));
        results.add(run("sanitizeSkinning rebinds an all-zero-weight vertex", ModelSelfTest::testSanitizeSkinningRebindsAllZeroWeightVertex));
        results.add(run("GPU skin shader guards a zero-weight vertex", ModelSelfTest::testGpuSkinMorphShaderZeroWeightGuard));
        results.add(run("alphaMode/alphaCutoff parsed with correct spec defaults", ModelSelfTest::testAlphaModeParsing));
        results.add(run("BLEND material resolves a distinct RenderType from OPAQUE on the same texture", ModelSelfTest::testTranslucentRenderTypeIsDistinct));
        results.add(run("material texCoord:1 reads uv1", ModelSelfTest::testTexCoordSelection));
        results.add(run("morph target weight blends onto the bind pose", ModelSelfTest::testMorphTargetBlend));
        results.add(run("texture ids are stable across loads", ModelSelfTest::testStableTextureKey));
        results.add(run("model bounds from bind pose", ModelSelfTest::testBounds));
        results.add(run("world culling box from 8 corners", ModelSelfTest::testWorldCullingBox));
        results.add(run("instance culling box tracks live pose, not bind pose", ModelSelfTest::testWorldCullingBoxTracksLivePose));
        results.add(run("voxelized cube fills the block", ModelSelfTest::testVoxelizeCube));
        results.add(run("greedy merge keeps box count sane", ModelSelfTest::testVoxelMerging));
        results.add(run("hollow mesh fills solid inside", ModelSelfTest::testSolidFill));
        results.add(run("hitbox grows to contain a rotated model", ModelSelfTest::testRotatedHitbox));
        results.add(run("block render yaw matches shape rotation", ModelSelfTest::testBlockRotationAgreement));
        results.add(run("rig reparents flat nodes onto a joint", ModelSelfTest::testRigging));
        results.add(run("sidecar metadata drives the rig", ModelSelfTest::testSidecarMetadata));
        results.add(run("pose angles match vanilla's flipped space", ModelSelfTest::testRotationConvention));
        results.add(run("animation expression arithmetic precedence", ModelSelfTest::testExpressionArithmeticPrecedence));
        results.add(run("animation expression comparison and boolean ops", ModelSelfTest::testExpressionComparisonAndBoolean));
        results.add(run("animation expression ternary", ModelSelfTest::testExpressionTernary));
        results.add(run("animation expression namespaces", ModelSelfTest::testExpressionNamespaces));
        results.add(run("animation expression compile cache", ModelSelfTest::testExpressionCaching));
        results.add(run("animation expression function calls", ModelSelfTest::testExpressionFunctionCalls));
        results.add(run("animation expression bare identifier is implicit query", ModelSelfTest::testExpressionBareIdentifierIsQuery));
        results.add(run("animation expression pi constant", ModelSelfTest::testExpressionPiConstant));
        results.add(run("animation expression math namespace functions", ModelSelfTest::testExpressionMathFunctions));
        results.add(run("animation expression new query fields", ModelSelfTest::testExpressionNewQueryFields));
        results.add(run("standard player preset registration", ModelSelfTest::testStandardPlayerPresetRegistration));
        results.add(run("standard player controller conditions evaluate", ModelSelfTest::testStandardPlayerControllerConditionsEvaluate));
        results.add(run("standard player attack state and transition", ModelSelfTest::testStandardPlayerAttackTransition));
        results.add(run("standard player angry-face layer gated by has_target", ModelSelfTest::testStandardPlayerAngryFaceLayer));
        results.add(run("skin material index override", ModelSelfTest::testSkinMaterialOverride));
        results.add(run("model instance attaches preset layers", ModelSelfTest::testModelInstanceAttachesPresetLayers));
        results.add(run("play mode ONCE clamps and reports ended", ModelSelfTest::testPlayModeOnce));
        results.add(run("play mode LOOP wraps modulo duration", ModelSelfTest::testPlayModeLoop));
        results.add(run("play mode CLAMP_FOREVER clamps without ending", ModelSelfTest::testPlayModeClampForever));
        results.add(run("play mode PING_PONG reflects both overshoot directions", ModelSelfTest::testPlayModePingPong));
        results.add(run("bone mask resolves by name and dotted path", ModelSelfTest::testBoneMaskResolvesByNameAndPath));
        results.add(run("bone mask exclude wins over include", ModelSelfTest::testBoneMaskExcludeWinsOverInclude));
        results.add(run("additive blend against a reference pose, not identity", ModelSelfTest::testAdditiveReferencePose));
        results.add(run("layer fade-in scales weight over its window", ModelSelfTest::testLayerFadeIn));
        results.add(run("clip fade-out scales weight then finishes the layer", ModelSelfTest::testClipFadeOut));
        results.add(run("layers compose in priority order, not insertion order", ModelSelfTest::testLayerPriorityOrder));
        results.add(run("procedural layer applies translation and scale", ModelSelfTest::testProceduralPoseTranslationAndScale));
        results.add(run("procedural bone rotation composes Rz*Ry*Rx", ModelSelfTest::testProceduralBoneRotationOrder));
        results.add(run("controller transition priority and tie-break", ModelSelfTest::testControllerTransitionPriorityAndTieBreak));
        results.add(run("controller exitTime gates a transition", ModelSelfTest::testControllerExitTime));
        results.add(run("controller crossfades mid-transition", ModelSelfTest::testControllerCrossfade));
        results.add(run("controller stays in a state whose own condition still outranks a lower-priority overlap", ModelSelfTest::testControllerPersistsInSelfWinningOverLowerPriorityOverlap));
        results.add(run("advance()/advanceAndPoseWith() skip a repeat call within the same render frame", ModelSelfTest::testAdvanceSkipsDoublePoseWithinSameFrame));
        results.add(run("LabPBR normal map inverts the green channel", ModelSelfTest::testLabPbrNormalMapGreenFlip));
        results.add(run("LabPBR normal map defaults occlusion/parallax when absent", ModelSelfTest::testLabPbrNormalMapDefaults));
        results.add(run("LabPBR smoothness/F0 bytes match known roughness/metallic values", ModelSelfTest::testLabPbrSmoothnessAndF0Bytes));
        results.add(run("LabPBR F0 byte never lands in the predefined-metal band", ModelSelfTest::testLabPbrF0NeverInMetalPresetBand));
        results.add(run("LabPBR specular map defaults metallic-roughness/emission when absent", ModelSelfTest::testLabPbrSpecularMapDefaults));
        return results;
    }

    private static Result run(String name, java.util.function.Supplier<String> check) {
        try {
            String failure = check.get();
            return failure == null ? new Result(name, true, "ok") : new Result(name, false, failure);
        } catch (Exception e) {
            return new Result(name, false, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    // ---- container / parsing ----

    private static String testGlbRoundTrip() {
        String json = "{\"asset\":{\"version\":\"2.0\"}}";
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
        byte[] bin = {1, 2, 3, 4};

        int totalLength = 12 + (8 + jsonBytes.length) + (8 + bin.length);
        ByteBuffer buffer = ByteBuffer.allocate(totalLength).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(0x46546C67);
        buffer.putInt(2);
        buffer.putInt(totalLength);
        buffer.putInt(jsonBytes.length);
        buffer.putInt(0x4E4F534A);
        buffer.put(jsonBytes);
        buffer.putInt(bin.length);
        buffer.putInt(0x004E4942);
        buffer.put(bin);

        byte[] glb = buffer.array();
        if (!GlbContainer.isGlb(glb)) {
            return "isGlb() returned false for a well-formed GLB buffer";
        }
        GlbContainer container = GlbContainer.parse(dummyId(), glb);
        if (!json.equals(container.json())) {
            return "JSON chunk mismatch: got '" + container.json() + "'";
        }
        if (!Arrays.equals(bin, container.binChunk())) {
            return "BIN chunk mismatch";
        }
        return null;
    }

    private static String testTriangleParse() {
        ModelDefinition definition = GltfModelParser.parse(dummyId(), trianglePositionsGltf(null, null).getBytes(StandardCharsets.UTF_8), null);
        ModelNode meshNode = findMeshNode(definition);
        if (meshNode == null) {
            return "no node with a mesh was imported";
        }
        Mesh mesh = meshNode.mesh();
        if (mesh.primitives().size() != 1) {
            return "expected 1 primitive, got " + mesh.primitives().size();
        }
        Primitive primitive = mesh.primitives().get(0);
        if (primitive.vertices().vertexCount() != 3) {
            return "expected 3 vertices, got " + primitive.vertices().vertexCount();
        }
        if (primitive.indices().length != 3 || primitive.indices()[2] != 2) {
            return "identity indices not generated: " + Arrays.toString(primitive.indices());
        }
        // NORMAL was absent, so flat face normals must have been generated — not the old (0,1,0) constant.
        if (!primitive.vertices().hasNormals()) {
            return "normals were not generated for a primitive with no NORMAL attribute";
        }
        float[] n = primitive.vertices().normals();
        if (Math.abs(Math.abs(n[2]) - 1f) > 1e-4f) {
            return "generated normal for a triangle in the XY plane should point along Z, got " + Arrays.toString(Arrays.copyOf(n, 3));
        }
        return null;
    }

    /**
     * A flat quad in the XY plane, UV-mapped so U tracks X and V tracks Y: every vertex should
     * recompute to the same tangent, {@code (1,0,0)} (U increases along +X), with handedness {@code
     * +1} (V increases along +Y, and {@code cross(normal, tangent) = (0,1,0)} already agrees with
     * that without flipping).
     */
    private static String testTangentRecompute() {
        float[] positions = {0f, 0f, 0f, 1f, 0f, 0f, 1f, 1f, 0f, 0f, 1f, 0f};
        float[] uv = {0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f};
        float[] normals = {0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f};
        int[] indices = {0, 1, 2, 0, 2, 3};

        float[] tangents = GeometryUtils.recalculateTangents(positions, normals, uv, indices);
        if (tangents.length != 16) {
            return "expected 4 tangents (16 floats), got " + tangents.length;
        }
        for (int v = 0; v < 4; v++) {
            float tx = tangents[v * 4];
            float ty = tangents[v * 4 + 1];
            float tz = tangents[v * 4 + 2];
            float w = tangents[v * 4 + 3];
            if (Math.abs(tx - 1f) > 1e-4f || Math.abs(ty) > 1e-4f || Math.abs(tz) > 1e-4f) {
                return "vertex " + v + ": expected tangent (1,0,0), got (" + tx + "," + ty + "," + tz + ")";
            }
            if (w != 1f) {
                return "vertex " + v + ": expected handedness +1, got " + w;
            }
        }
        return null;
    }

    /**
     * {@link GeometryUtils#sanitizeTangents} is what closed the real bug this session found (a
     * converted rig's own file-provided TANGENT accessor shipped near-zero-length entries, which
     * every consumer's normalize() turned into NaN) — but it was previously only ever exercised
     * indirectly through the full importer. Directly checks a hand-built degenerate input (one
     * zero-length tangent, one with a NaN component) against a valid normals array: every output must
     * be finite, unit-length, and carry a handedness of exactly +1 or -1.
     */
    private static String testSanitizeTangentsDegenerateInput() {
        float[] normals = {0f, 0f, 1f, 0f, 0f, 1f};
        float[] tangents = {0f, 0f, 0f, 1f, Float.NaN, 1f, 0f, Float.NaN};
        GeometryUtils.sanitizeTangents(tangents, normals);
        for (int v = 0; v < 2; v++) {
            float x = tangents[v * 4], y = tangents[v * 4 + 1], z = tangents[v * 4 + 2], w = tangents[v * 4 + 3];
            if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z) || !Float.isFinite(w)) {
                return "vertex " + v + ": expected a fully finite result, got (" + x + "," + y + "," + z + "," + w + ")";
            }
            float length = (float) Math.sqrt(x * x + y * y + z * z);
            if (Math.abs(length - 1f) > 1e-4f) {
                return "vertex " + v + ": expected a unit-length tangent, got length " + length;
            }
            if (w != 1f && w != -1f) {
                return "vertex " + v + ": expected handedness +-1, got " + w;
            }
        }
        return null;
    }

    /**
     * A file whose NORMAL accessor declares fewer vertices than POSITION must be rejected outright
     * (structural malformation — see GltfMeshImporter's own doc on the throw-vs-sanitize policy),
     * not silently produce a shorter array later indexed as if it matched.
     */
    private static String testGltfMeshImporterRejectsCrossAttributeLengthMismatch() {
        float[] positions = {0f, 0f, 0f, 1f, 0f, 0f, 1f, 1f, 0f};
        float[] normals = {0f, 0f, 1f, 0f, 0f, 1f};
        ByteBuffer buffer = ByteBuffer.allocate((positions.length + normals.length) * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float f : positions) {
            buffer.putFloat(f);
        }
        for (float f : normals) {
            buffer.putFloat(f);
        }
        String base64 = Base64.getEncoder().encodeToString(buffer.array());
        int positionBytes = positions.length * 4;
        int normalBytes = normals.length * 4;

        String json = "{"
                + "\"asset\":{\"version\":\"2.0\"},"
                + "\"buffers\":[{\"uri\":\"data:application/octet-stream;base64," + base64 + "\",\"byteLength\":" + buffer.capacity() + "}],"
                + "\"bufferViews\":["
                + "  {\"buffer\":0,\"byteOffset\":0,\"byteLength\":" + positionBytes + "},"
                + "  {\"buffer\":0,\"byteOffset\":" + positionBytes + ",\"byteLength\":" + normalBytes + "}"
                + "],"
                + "\"accessors\":["
                // POSITION: 3 vertices - NORMAL: only 2, a real disagreement between two independent accessors.
                + "  {\"bufferView\":0,\"componentType\":5126,\"count\":3,\"type\":\"VEC3\"},"
                + "  {\"bufferView\":1,\"componentType\":5126,\"count\":2,\"type\":\"VEC3\"}"
                + "],"
                + "\"meshes\":[{\"primitives\":[{\"attributes\":{\"POSITION\":0,\"NORMAL\":1}}]}],"
                + "\"nodes\":[{\"name\":\"tri\",\"mesh\":0}],"
                + "\"scenes\":[{\"nodes\":[0]}],\"scene\":0"
                + "}";
        try {
            GltfModelParser.parse(dummyId(), json.getBytes(StandardCharsets.UTF_8), null);
            return "expected a ModelFormatException for a mismatched NORMAL accessor length";
        } catch (ModelFormatException expected) {
            return null;
        }
    }

    /**
     * An index accessor value outside {@code [0, vertexCount)} must be rejected — left unchecked, it
     * either throws deep inside GeometryUtils on the CPU path or reaches glDrawElements undefined on
     * the GPU one, neither of which points back at the actual malformed file field.
     */
    private static String testGltfMeshImporterRejectsOutOfRangeIndices() {
        float[] positions = {0f, 0f, 0f, 1f, 0f, 0f, 1f, 1f, 0f};
        int positionBytes = positions.length * 4;
        int[] indices = {0, 1, 3};
        int indexBytes = indices.length * 4;

        ByteBuffer buffer = ByteBuffer.allocate(positionBytes + indexBytes).order(ByteOrder.LITTLE_ENDIAN);
        for (float f : positions) {
            buffer.putFloat(f);
        }
        for (int i : indices) {
            buffer.putInt(i);
        }
        String base64 = Base64.getEncoder().encodeToString(buffer.array());

        String json = "{"
                + "\"asset\":{\"version\":\"2.0\"},"
                + "\"buffers\":[{\"uri\":\"data:application/octet-stream;base64," + base64 + "\",\"byteLength\":" + buffer.capacity() + "}],"
                + "\"bufferViews\":["
                + "  {\"buffer\":0,\"byteOffset\":0,\"byteLength\":" + positionBytes + "},"
                + "  {\"buffer\":0,\"byteOffset\":" + positionBytes + ",\"byteLength\":" + indexBytes + "}"
                + "],"
                + "\"accessors\":["
                + "  {\"bufferView\":0,\"componentType\":5126,\"count\":3,\"type\":\"VEC3\"},"
                // index 3 is out of range for 3 vertices (valid indices are 0,1,2)
                + "  {\"bufferView\":1,\"componentType\":5125,\"count\":3,\"type\":\"SCALAR\"}"
                + "],"
                + "\"meshes\":[{\"primitives\":[{\"attributes\":{\"POSITION\":0},\"indices\":1}]}],"
                + "\"nodes\":[{\"name\":\"tri\",\"mesh\":0}],"
                + "\"scenes\":[{\"nodes\":[0]}],\"scene\":0"
                + "}";
        try {
            GltfModelParser.parse(dummyId(), json.getBytes(StandardCharsets.UTF_8), null);
            return "expected a ModelFormatException for an index (3) out of range for 3 vertices";
        } catch (ModelFormatException expected) {
            return null;
        }
    }

    /** A skin object missing the required "joints" array must fail with a clear ModelFormatException, not a raw NullPointerException. */
    private static String testImportSkinsRejectsMissingJoints() {
        String json = "{"
                + "\"asset\":{\"version\":\"2.0\"},"
                + "\"skins\":[{}],"
                + "\"nodes\":[{\"name\":\"root\"}],"
                + "\"scenes\":[{\"nodes\":[0]}],\"scene\":0"
                + "}";
        try {
            GltfModelParser.parse(dummyId(), json.getBytes(StandardCharsets.UTF_8), null);
            return "expected a ModelFormatException for a skin with no joints array";
        } catch (ModelFormatException expected) {
            return null;
        }
    }

    /**
     * A skin declaring 2 joints but only 1 inverse bind matrix must fail clearly at import time — the
     * two counts come from completely independent parts of the file and are never otherwise
     * cross-checked, so a mismatch previously surfaced as a confusing ArrayIndexOutOfBoundsException
     * much later inside CpuSkinner instead.
     */
    private static String testImportSkinsRejectsMismatchedInverseBindCount() {
        float[] identity = {1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f};
        ByteBuffer matrixBuffer = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN);
        for (float f : identity) {
            matrixBuffer.putFloat(f);
        }
        String matrixBytes = Base64.getEncoder().encodeToString(matrixBuffer.array());
        String json = "{"
                + "\"asset\":{\"version\":\"2.0\"},"
                + "\"buffers\":[{\"uri\":\"data:application/octet-stream;base64," + matrixBytes + "\",\"byteLength\":64}],"
                + "\"bufferViews\":[{\"buffer\":0,\"byteOffset\":0,\"byteLength\":64}],"
                + "\"accessors\":[{\"bufferView\":0,\"componentType\":5126,\"count\":1,\"type\":\"MAT4\"}],"
                + "\"skins\":[{\"joints\":[0,1],\"inverseBindMatrices\":0}],"
                + "\"nodes\":[{\"name\":\"a\"},{\"name\":\"b\"}],"
                + "\"scenes\":[{\"nodes\":[0,1]}],\"scene\":0"
                + "}";
        try {
            GltfModelParser.parse(dummyId(), json.getBytes(StandardCharsets.UTF_8), null);
            return "expected a ModelFormatException for 2 joints but 1 inverse bind matrix";
        } catch (ModelFormatException expected) {
            return null;
        }
    }

    /**
     * Direct unit test of {@link GeometryUtils#sanitizeSkinning}: an out-of-range and a negative joint
     * index must both clamp into {@code [0, jointCount)}, and non-normalized weights must end up
     * summing to 1.
     */
    private static String testSanitizeSkinningClampsOutOfRangeJointsAndNormalizesWeights() {
        int[] joints = {5, -1, 0, 0};
        float[] weights = {2f, 2f, 0f, 0f};
        GeometryUtils.sanitizeSkinning(joints, weights, 2);
        for (int j : joints) {
            if (j < 0 || j >= 2) {
                return "expected every joint index in [0,2), got " + Arrays.toString(joints);
            }
        }
        float sum = weights[0] + weights[1] + weights[2] + weights[3];
        if (Math.abs(sum - 1f) > 1e-4f) {
            return "expected weights to sum to 1, got " + sum + " (" + Arrays.toString(weights) + ")";
        }
        return null;
    }

    /**
     * A vertex whose file-provided WEIGHTS_0 are all zero (a real, observed case on a converted rig)
     * must be rebound fully onto joint 0 with weight 1 rather than left as a zero vector that would
     * otherwise divide gltf_skin_morph.vsh's homogeneous position by a zero w.
     */
    private static String testSanitizeSkinningRebindsAllZeroWeightVertex() {
        int[] joints = {3, 4, 5, 6};
        float[] weights = {0f, 0f, 0f, 0f};
        GeometryUtils.sanitizeSkinning(joints, weights, 8);
        if (joints[0] != 0 || joints[1] != 0 || joints[2] != 0 || joints[3] != 0) {
            return "expected an all-zero-weight vertex rebound fully onto joint 0, got " + Arrays.toString(joints);
        }
        if (weights[0] != 1f || weights[1] != 0f || weights[2] != 0f || weights[3] != 0f) {
            return "expected weights (1,0,0,0), got " + Arrays.toString(weights);
        }
        return null;
    }

    /**
     * The third (real-GL) check in this suite, built exactly like {@link #testGpuSkinningMatchesCpu}:
     * a single vertex whose WEIGHTS_0 are all zero must still produce a finite result from the real
     * transform-feedback shader (the bind-pose fallback documented in gltf_skin_morph.vsh), not the
     * 0/0 NaN an unguarded weighted sum would otherwise divide by.
     */
    private static String testGpuSkinMorphShaderZeroWeightGuard() {
        Skin skin = new Skin(new int[]{0}, new Matrix4f[]{new Matrix4f()});
        int[] joints = {0, 0, 0, 0};
        float[] weights = {0f, 0f, 0f, 0f};
        Vector3f bindPosition = new Vector3f(1f, 2f, 3f);
        Vector3f bindNormal = new Vector3f(0f, 1f, 0f);

        VertexData vertices = new VertexData(
                new float[]{bindPosition.x, bindPosition.y, bindPosition.z},
                new float[]{bindNormal.x, bindNormal.y, bindNormal.z},
                new float[0], new float[0], new float[0], joints, weights, 1);
        Primitive primitive = new Primitive(vertices, new int[]{0}, MaterialData.untextured(), List.of());

        GpuSkinBuffers buffers = new GpuSkinBuffers(primitive, skin);
        try {
            Matrix4f[] skinMatrices = {new Matrix4f()};
            buffers.updateAndSkin(skinMatrices, null, new Matrix4f(), new Matrix3f());

            Vector3f gpuPosition = readVec3(buffers.outPositionBufferId());
            Vector3f gpuNormal = readVec3(buffers.outNormalBufferId());

            if (!Float.isFinite(gpuPosition.x) || !Float.isFinite(gpuPosition.y) || !Float.isFinite(gpuPosition.z)) {
                return "expected a finite position for an all-zero-weight vertex, got " + gpuPosition;
            }
            if (!Float.isFinite(gpuNormal.x) || !Float.isFinite(gpuNormal.y) || !Float.isFinite(gpuNormal.z)) {
                return "expected a finite normal for an all-zero-weight vertex, got " + gpuNormal;
            }
            if (gpuPosition.distance(bindPosition) > 1e-3f) {
                return "expected the bind-pose fallback position " + bindPosition + ", got " + gpuPosition;
            }
        } finally {
            buffers.destroy();
        }
        return null;
    }

    /**
     * {@code alphaMode}/{@code alphaCutoff} are untrusted file input — this checks every case
     * {@link GltfMaterialImporter#importOne} branches on: an explicit value of each of the three spec
     * strings, a lower-case string (case-insensitive matching), an absent field (spec default {@code
     * OPAQUE}), and an unrecognized string (falls back to {@code OPAQUE} rather than throwing, since
     * this is semantically-degenerate-but-structurally-legal data, not a format error).
     */
    private static String testAlphaModeParsing() {
        GltfDocument.GltfMaterial blend = new GltfDocument.GltfMaterial();
        blend.alphaMode = "BLEND";
        GltfDocument.GltfMaterial mask = new GltfDocument.GltfMaterial();
        mask.alphaMode = "mask";
        mask.alphaCutoff = 0.9f;
        GltfDocument.GltfMaterial opaqueExplicit = new GltfDocument.GltfMaterial();
        opaqueExplicit.alphaMode = "OPAQUE";
        GltfDocument.GltfMaterial absent = new GltfDocument.GltfMaterial();
        GltfDocument.GltfMaterial garbage = new GltfDocument.GltfMaterial();
        garbage.alphaMode = "not-a-real-mode";

        GltfDocument document = new GltfDocument();
        document.materials = List.of(blend, mask, opaqueExplicit, absent, garbage);
        List<MaterialData> materials = new GltfMaterialImporter(document, null, dummyId()).importAll();

        if (materials.get(0).alphaMode() != AlphaMode.BLEND) {
            return "expected BLEND, got " + materials.get(0).alphaMode();
        }
        if (materials.get(1).alphaMode() != AlphaMode.MASK || Math.abs(materials.get(1).alphaCutoff() - 0.9f) > 1e-6f) {
            return "expected MASK with cutoff 0.9 (case-insensitive), got " + materials.get(1).alphaMode() + "/" + materials.get(1).alphaCutoff();
        }
        if (materials.get(2).alphaMode() != AlphaMode.OPAQUE) {
            return "expected explicit OPAQUE, got " + materials.get(2).alphaMode();
        }
        if (materials.get(3).alphaMode() != AlphaMode.OPAQUE || Math.abs(materials.get(3).alphaCutoff() - 0.5f) > 1e-6f) {
            return "expected absent alphaMode to default to OPAQUE with cutoff 0.5, got "
                    + materials.get(3).alphaMode() + "/" + materials.get(3).alphaCutoff();
        }
        if (materials.get(4).alphaMode() != AlphaMode.OPAQUE) {
            return "expected an unrecognized alphaMode string to fall back to OPAQUE, got " + materials.get(4).alphaMode();
        }
        return null;
    }

    /**
     * {@code ModelRenderTypes.entityTriangles} caches by texture within two separate maps (one per
     * alpha-mode bucket, see that method's own doc) — an opaque and a translucent material sharing one
     * texture must never collide and silently resolve to whichever RenderType happened to be built
     * first. Pure-JVM: constructing a {@code RenderType} composes shard lambdas, it doesn't need a
     * live GL context (only actually running {@code setupRenderState()} would).
     */
    private static String testTranslucentRenderTypeIsDistinct() {
        ResourceLocation texture = dummyId();
        MaterialData opaque = MaterialData.untextured();
        MaterialData blend = new MaterialData(null, null, MaterialData.WHITE, 0,
                null, null, 1f, 1f, null, 1f, null, MaterialData.BLACK, AlphaMode.BLEND, 0.5f, "", null, null);

        RenderType opaqueType = ModelRenderTypes.entityTriangles(texture, opaque);
        RenderType blendType = ModelRenderTypes.entityTriangles(texture, blend);
        if (opaqueType == blendType) {
            return "expected a BLEND material to resolve a different RenderType than an OPAQUE one sharing the same texture";
        }
        return null;
    }

    /**
     * A material whose base color texture points at {@code TEXCOORD_1} must render with {@code
     * uv1}, not {@code uv0} — before {@code MaterialData} carried a {@code texCoord} at all, this
     * silently used the wrong UV set with no error.
     */
    private static String testTexCoordSelection() {
        float[] positions = {0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f};
        float[] uv0 = {0f, 0f, 0f, 0f, 0f, 0f};
        float[] uv1 = {0.25f, 0.75f, 0.25f, 0.75f, 0.25f, 0.75f};
        VertexData vertices = new VertexData(positions, new float[0], new float[0], uv0, uv1, new int[0], new float[0], 3);
        MaterialData material = new MaterialData(null, null, MaterialData.WHITE, 1,
                null, null, 1f, 1f, null, 1f, null, MaterialData.BLACK, AlphaMode.OPAQUE, 0.5f, "", null, null);
        Primitive primitive = new Primitive(vertices, new int[]{0, 1, 2}, material, List.of());

        RecordingConsumer consumer = new RecordingConsumer();
        new com.dimalab.storymodengine.client.model.ModelRenderer()
                .emitPrimitive(consumer, null, primitive, new float[0], new PoseStack().last().pose(), new PoseStack().last().normal(), 0xF000F0, 0);

        if (consumer.u[0] != 0.25f || consumer.v[0] != 0.75f) {
            return "texCoord:1 should read from uv1, got u=" + consumer.u[0] + " v=" + consumer.v[0];
        }
        return null;
    }

    /**
     * A primitive with one morph target: weight 0 must reproduce the bind pose exactly, weight 1 the
     * bind pose plus the target's full delta.
     */
    private static String testMorphTargetBlend() {
        float[] positions = {0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f};
        float[] positionDeltas = {0f, 0f, 5f, 0f, 0f, 5f, 0f, 0f, 5f};
        VertexData vertices = new VertexData(positions, new float[0], new float[0], new float[0], new float[0], new int[0], new float[0], 3);
        Primitive.MorphTarget target = new Primitive.MorphTarget(positionDeltas, new float[0]);
        Primitive primitive = new Primitive(vertices, new int[]{0, 1, 2}, MaterialData.untextured(), List.of(target));

        RecordingConsumer atZero = new RecordingConsumer();
        new com.dimalab.storymodengine.client.model.ModelRenderer()
                .emitPrimitive(atZero, null, primitive, new float[]{0f}, new PoseStack().last().pose(), new PoseStack().last().normal(), 0xF000F0, 0);
        if (atZero.z[0] != 0f) {
            return "weight 0 must reproduce the bind pose, got z=" + atZero.z[0];
        }

        RecordingConsumer atOne = new RecordingConsumer();
        new com.dimalab.storymodengine.client.model.ModelRenderer()
                .emitPrimitive(atOne, null, primitive, new float[]{1f}, new PoseStack().last().pose(), new PoseStack().last().normal(), 0xF000F0, 0);
        if (Math.abs(atOne.z[0] - 5f) > 1e-4f) {
            return "weight 1 must apply the full delta, expected z=5, got " + atOne.z[0];
        }
        return null;
    }

    /**
     * A sparse accessor stores a base array plus a short list of index/value overrides. Reading only
     * the base — which is what the importer did before — yields silently wrong geometry: no error, no
     * warning, just vertices in the wrong places.
     *
     * <p>Three positions, with the middle one overridden to (9,9,9) via sparse.
     */
    private static String testSparseAccessor() {
        float[] base = {0f, 0f, 0f, 1f, 1f, 1f, 2f, 2f, 2f};
        float[] overrideValues = {9f, 9f, 9f};
        int[] overrideIndices = {1};

        int baseBytes = base.length * 4;
        int indexBytes = overrideIndices.length * 4;
        int valueBytes = overrideValues.length * 4;

        ByteBuffer buffer = ByteBuffer.allocate(baseBytes + indexBytes + valueBytes).order(ByteOrder.LITTLE_ENDIAN);
        for (float f : base) {
            buffer.putFloat(f);
        }
        for (int i : overrideIndices) {
            buffer.putInt(i);
        }
        for (float f : overrideValues) {
            buffer.putFloat(f);
        }
        String base64 = Base64.getEncoder().encodeToString(buffer.array());

        String json = "{"
                + "\"asset\":{\"version\":\"2.0\",\"generator\":\"Blockbench\"},"
                + "\"buffers\":[{\"uri\":\"data:application/octet-stream;base64," + base64 + "\",\"byteLength\":" + buffer.capacity() + "}],"
                + "\"bufferViews\":["
                + "  {\"buffer\":0,\"byteOffset\":0,\"byteLength\":" + baseBytes + "},"
                + "  {\"buffer\":0,\"byteOffset\":" + baseBytes + ",\"byteLength\":" + indexBytes + "},"
                + "  {\"buffer\":0,\"byteOffset\":" + (baseBytes + indexBytes) + ",\"byteLength\":" + valueBytes + "}"
                + "],"
                + "\"accessors\":[{\"bufferView\":0,\"componentType\":5126,\"count\":3,\"type\":\"VEC3\","
                + "  \"sparse\":{\"count\":1,"
                + "    \"indices\":{\"bufferView\":1,\"componentType\":5125},"
                + "    \"values\":{\"bufferView\":2}}}],"
                + "\"meshes\":[{\"primitives\":[{\"attributes\":{\"POSITION\":0}}]}],"
                + "\"nodes\":[{\"name\":\"tri\",\"mesh\":0}],"
                + "\"scenes\":[{\"nodes\":[0]}],\"scene\":0"
                + "}";

        ModelDefinition definition = GltfModelParser.parse(dummyId(), json.getBytes(StandardCharsets.UTF_8), null);
        ModelNode node = findMeshNode(definition);
        if (node == null) {
            return "no mesh imported";
        }
        float[] positions = node.mesh().primitives().get(0).vertices().positions();
        float[] expected = {0f, 0f, 0f, 9f, 9f, 9f, 2f, 2f, 2f};
        if (!nearlyEqual(positions, expected)) {
            return "sparse override not applied: got " + Arrays.toString(positions) + ", expected " + Arrays.toString(expected);
        }
        return null;
    }

    /**
     * A sparse accessor may legally omit its own {@code bufferView} entirely — the spec then says the
     * base is all zero, only the sparse entries carry real data. HollowEngine's own accessor reader
     * treats this the same way (its base stream is simply absent, and every non-overridden read falls
     * back to 0). Worth its own case: it is a different code path than "base array plus overrides"
     * above — no base loop runs at all — and an importer that assumes a bufferView always exists would
     * throw or read garbage here instead of zero-filling.
     */
    private static String testSparseAccessorWithoutBase() {
        int[] overrideIndices = {1};
        float[] overrideValues = {9f, 9f, 9f};

        int indexBytes = overrideIndices.length * 4;
        int valueBytes = overrideValues.length * 4;

        ByteBuffer buffer = ByteBuffer.allocate(indexBytes + valueBytes).order(ByteOrder.LITTLE_ENDIAN);
        for (int i : overrideIndices) {
            buffer.putInt(i);
        }
        for (float f : overrideValues) {
            buffer.putFloat(f);
        }
        String base64 = Base64.getEncoder().encodeToString(buffer.array());

        String json = "{"
                + "\"asset\":{\"version\":\"2.0\",\"generator\":\"Blockbench\"},"
                + "\"buffers\":[{\"uri\":\"data:application/octet-stream;base64," + base64 + "\",\"byteLength\":" + buffer.capacity() + "}],"
                + "\"bufferViews\":["
                + "  {\"buffer\":0,\"byteOffset\":0,\"byteLength\":" + indexBytes + "},"
                + "  {\"buffer\":0,\"byteOffset\":" + indexBytes + ",\"byteLength\":" + valueBytes + "}"
                + "],"
                + "\"accessors\":[{\"componentType\":5126,\"count\":3,\"type\":\"VEC3\","
                + "  \"sparse\":{\"count\":1,"
                + "    \"indices\":{\"bufferView\":0,\"componentType\":5125},"
                + "    \"values\":{\"bufferView\":1}}}],"
                + "\"meshes\":[{\"primitives\":[{\"attributes\":{\"POSITION\":0}}]}],"
                + "\"nodes\":[{\"name\":\"tri\",\"mesh\":0}],"
                + "\"scenes\":[{\"nodes\":[0]}],\"scene\":0"
                + "}";

        ModelDefinition definition = GltfModelParser.parse(dummyId(), json.getBytes(StandardCharsets.UTF_8), null);
        ModelNode node = findMeshNode(definition);
        if (node == null) {
            return "no mesh imported";
        }
        float[] positions = node.mesh().primitives().get(0).vertices().positions();
        float[] expected = {0f, 0f, 0f, 9f, 9f, 9f, 0f, 0f, 0f};
        if (!nearlyEqual(positions, expected)) {
            return "bufferView-less sparse accessor not zero-filled correctly: got "
                    + Arrays.toString(positions) + ", expected " + Arrays.toString(expected);
        }
        return null;
    }

    /**
     * The external model folder's guard must fail closed: a path that normalizes outside the
     * trusted root ({@code ../../../etc/passwd} is a legal {@link ResourceLocation} string — its
     * characters are all individually valid, {@link ResourceLocation} doesn't parse structure) must
     * never report as existing or be read, the same way a legitimate reference that stays inside
     * the root would be. This can't prove the *positive* case without planting a real file under
     * the external root, which would make the suite depend on filesystem state; the safety property
     * that actually matters — never leaking a path that escapes — doesn't need that.
     */
    private static String testExternalModelIoRejectsTraversal() {
        ResourceLocation traversal = new ResourceLocation("storymodengine", "../../../../etc/passwd");
        if (ExternalModelIO.exists(traversal)) {
            return "a path escaping the external root must never report as existing";
        }
        if (ExternalModelIO.read(traversal) != null) {
            return "a path escaping the external root must never be read";
        }
        return null;
    }

    /** A parent node with a child; both must survive import with the parent link and the child's own transform intact. */
    private static String testHierarchyPreserved() {
        String json = "{"
                + "\"asset\":{\"version\":\"2.0\",\"generator\":\"Blockbench\"},"
                + "\"nodes\":["
                + "  {\"name\":\"parent\",\"translation\":[1,0,0],\"children\":[1]},"
                + "  {\"name\":\"child\",\"translation\":[0,2,0]}"
                + "],"
                + "\"scenes\":[{\"nodes\":[0]}],\"scene\":0"
                + "}";
        ModelDefinition definition = GltfModelParser.parse(dummyId(), json.getBytes(StandardCharsets.UTF_8), null);

        ModelNode parent = definition.nodeByName("parent");
        ModelNode child = definition.nodeByName("child");
        if (parent == null || child == null) {
            return "parent/child node missing after import";
        }
        if (child.parent() != parent) {
            return "child's parent link was not established";
        }
        if (parent.children().size() != 1) {
            return "parent should have exactly 1 child, has " + parent.children().size();
        }
        if (Math.abs(child.bindTranslation().y - 2f) > 1e-5f) {
            return "child's own bind translation was lost: " + child.bindTranslation();
        }

        // And the runtime hierarchy must compose them: child's global position = (1, 2, 0).
        ModelInstance instance = new ModelInstance(definition);
        RuntimeNode runtimeChild = instance.nodesByIndex().get(child.index());
        Vector3f global = runtimeChild.globalMatrix().getTranslation(new Vector3f());
        if (global.distance(new Vector3f(1f, 2f, 0f)) > 1e-4f) {
            return "composed child global position expected (1,2,0), got " + global;
        }
        return null;
    }

    /** A node given as a 4x4 matrix must be decomposed, not ignored. */
    private static String testMatrixDecomposition() {
        // Column-major translation-by-(5, 6, 7) matrix.
        String json = "{"
                + "\"asset\":{\"version\":\"2.0\",\"generator\":\"Blockbench\"},"
                + "\"nodes\":[{\"name\":\"m\",\"matrix\":[1,0,0,0, 0,1,0,0, 0,0,1,0, 5,6,7,1]}],"
                + "\"scenes\":[{\"nodes\":[0]}],\"scene\":0"
                + "}";
        ModelDefinition definition = GltfModelParser.parse(dummyId(), json.getBytes(StandardCharsets.UTF_8), null);
        ModelNode node = definition.nodeByName("m");
        if (node == null) {
            return "node missing after import";
        }
        if (node.bindTranslation().distance(new Vector3f(5f, 6f, 7f)) > 1e-4f) {
            return "matrix translation not decomposed, got " + node.bindTranslation();
        }
        return null;
    }

    private static String testFacingDetection() {
        if (ModelSpace.needsFacingCorrection("Blockbench 4.9")) {
            return "Blockbench export should not need facing correction";
        }
        if (!ModelSpace.needsFacingCorrection("Khronos glTF Blender I/O v3.6.27")) {
            return "Blender export should need facing correction";
        }
        if (!ModelSpace.needsFacingCorrection(null)) {
            return "an unknown generator should default to needing correction";
        }
        // A corrected model gains exactly one synthetic root wrapping the original one.
        String json = "{\"asset\":{\"version\":\"2.0\",\"generator\":\"Blender\"},"
                + "\"nodes\":[{\"name\":\"real\"}],\"scenes\":[{\"nodes\":[0]}],\"scene\":0}";
        ModelDefinition corrected = GltfModelParser.parse(dummyId(), json.getBytes(StandardCharsets.UTF_8), null);
        if (corrected.roots().size() != 1 || corrected.roots().get(0).index() != ModelSpace.ROOT_INDEX) {
            return "expected a synthetic ModelSpace root wrapping the real one";
        }
        if (corrected.nodeByName("real") == null) {
            return "the real node disappeared under the correction root";
        }
        return null;
    }

    // ---- animation ----

    /**
     * A rotation track whose absolute keyframe equals the node's bind rotation must import as an
     * <i>identity</i> delta — that's the whole invariant blending depends on.
     */
    private static String testAnimationDeltas() {
        // Node bind rotation = 90 deg about Y; animation keyframe 0 has that same absolute rotation.
        Quaternionf bind = new Quaternionf().rotateY((float) Math.PI / 2f);
        float[] times = {0f, 1f};
        // Absolute values: [bind, bind]; encoded as a float accessor via a data URI.
        float[] values = {bind.x, bind.y, bind.z, bind.w, bind.x, bind.y, bind.z, bind.w};

        String json = "{"
                + "\"asset\":{\"version\":\"2.0\",\"generator\":\"Blockbench\"},"
                + "\"nodes\":[{\"name\":\"bone\",\"rotation\":[" + bind.x + "," + bind.y + "," + bind.z + "," + bind.w + "]}],"
                + "\"scenes\":[{\"nodes\":[0]}],\"scene\":0,"
                + "\"buffers\":[" + floatBuffer(concat(times, values)) + "],"
                + "\"bufferViews\":[{\"buffer\":0,\"byteOffset\":0,\"byteLength\":" + (times.length * 4) + "},"
                + "                 {\"buffer\":0,\"byteOffset\":" + (times.length * 4) + ",\"byteLength\":" + (values.length * 4) + "}],"
                + "\"accessors\":[{\"bufferView\":0,\"componentType\":5126,\"count\":2,\"type\":\"SCALAR\"},"
                + "               {\"bufferView\":1,\"componentType\":5126,\"count\":2,\"type\":\"VEC4\"}],"
                + "\"animations\":[{\"name\":\"spin\",\"samplers\":[{\"input\":0,\"output\":1,\"interpolation\":\"LINEAR\"}],"
                + "                 \"channels\":[{\"sampler\":0,\"target\":{\"node\":0,\"path\":\"rotation\"}}]}]"
                + "}";

        ModelDefinition definition = GltfModelParser.parse(dummyId(), json.getBytes(StandardCharsets.UTF_8), null);
        AnimationClip clip = definition.animation("spin");
        if (clip == null) {
            return "animation 'spin' was not imported";
        }
        ModelNode bone = definition.nodeByName("bone");
        AnimationData data = clip.nodes().get(bone.index());
        if (data == null || data.rotation == null) {
            return "rotation track missing for the animated node";
        }
        Quaternionf delta = data.rotation.sample(0f);
        Quaternionf identity = new Quaternionf();
        float dot = Math.abs(delta.x * identity.x + delta.y * identity.y + delta.z * identity.z + delta.w * identity.w);
        if (Math.abs(dot - 1f) > 1e-4f) {
            return "a keyframe equal to the bind rotation should store an identity delta, got " + delta;
        }
        return null;
    }

    /** OVERRIDE at weight 0.5 must land exactly halfway between bind pose and the clip's target. */
    private static String testPoseBlending() {
        ModelNode bone = new ModelNode(0, "bone", new Vector3f(0f, 0f, 0f), new Quaternionf(), new Vector3f(1f, 1f, 1f), null, null);
        ModelDefinition definition = new ModelDefinition(dummyId(), List.of(bone), List.of(clipTranslating(0, new Vector3f(0f, 10f, 0f))));

        ModelInstance instance = new ModelInstance(definition);
        instance.animator().addLayer(new ClipLayer(ClipAnimationLayerSpec.of("test", "move")
                .withPlayMode(AnimationPlayMode.LOOP)
                .withWeight(AnimationExpression.of("0.5"))
                .withBlendMode(LayerBlendMode.OVERRIDE)));
        instance.pose();

        Vector3f result = instance.nodesByIndex().get(0).translation();
        if (result.distance(new Vector3f(0f, 5f, 0f)) > 1e-4f) {
            return "override at weight 0.5 should give (0,5,0), got " + result;
        }
        return null;
    }

    /** Two additive layers must stack, not overwrite — the thing absolute-valued animation could never do. */
    private static String testAdditiveLayering() {
        ModelNode bone = new ModelNode(0, "bone", new Vector3f(), new Quaternionf(), new Vector3f(1f, 1f, 1f), null, null);
        AnimationClip up = clipTranslating(0, new Vector3f(0f, 3f, 0f));
        AnimationClip side = new AnimationClip("side", clipTranslating(0, new Vector3f(4f, 0f, 0f)).nodes(), 1f);
        ModelDefinition definition = new ModelDefinition(dummyId(), List.of(bone), List.of(up, side));

        ModelInstance instance = new ModelInstance(definition);
        instance.animator().addLayer(new ClipLayer(ClipAnimationLayerSpec.of("layer-up", up.name())
                .withPlayMode(AnimationPlayMode.LOOP)
                .withBlendMode(LayerBlendMode.ADDITIVE)));
        instance.animator().addLayer(new ClipLayer(ClipAnimationLayerSpec.of("layer-side", side.name())
                .withPlayMode(AnimationPlayMode.LOOP)
                .withBlendMode(LayerBlendMode.ADDITIVE)));
        instance.pose();

        Vector3f result = instance.nodesByIndex().get(0).translation();
        if (result.distance(new Vector3f(4f, 3f, 0f)) > 1e-4f) {
            return "two additive layers should sum to (4,3,0), got " + result;
        }
        return null;
    }

    private static String testSkinningMath() {
        // joint0 root at origin; joint1 a child offset (0,1,0) at bind. Animated so joint1's local
        // translation goes (0,1,0) -> (0,3,0); at t=0.5 linear, joint1 sits at world (0,2,0).
        ModelNode joint0 = new ModelNode(0, "root", new Vector3f(), new Quaternionf(), new Vector3f(1f, 1f, 1f), null, null);
        ModelNode joint1 = new ModelNode(1, "child", new Vector3f(0f, 1f, 0f), new Quaternionf(), new Vector3f(1f, 1f, 1f), null, null);
        joint0.addChild(joint1);

        Skin skin = new Skin(new int[]{0, 1}, new Matrix4f[]{
                new Matrix4f(),
                new Matrix4f().translate(0f, -1f, 0f), // undoes joint1's bind-pose world position
        });

        // Delta track: (0,0,0) at t=0 -> (0,2,0) at t=1, i.e. absolute (0,1,0) -> (0,3,0).
        AnimationData data = new AnimationData();
        data.translation = new Vec3Track(new float[]{0f, 1f},
                new Vector3f[]{new Vector3f(0f, 0f, 0f), new Vector3f(0f, 2f, 0f)}, false);
        Map<Integer, AnimationData> nodes = new LinkedHashMap<>();
        nodes.put(1, data);
        AnimationClip clip = AnimationClip.of("rise", nodes);

        ModelDefinition definition = new ModelDefinition(dummyId(), List.of(joint0), List.of(clip));
        ModelInstance instance = new ModelInstance(definition);
        instance.animator().addLayer(new ClipLayer(ClipAnimationLayerSpec.of("test", clip.name())
                .withPlayMode(AnimationPlayMode.CLAMP_FOREVER)));
        instance.advance(0.5f);

        Matrix4f[] skinMatrices = CpuSkinner.computeSkinMatrices(skin, instance.nodesByIndex(), null);
        Vector3f skinned = new Vector3f();
        CpuSkinner.skinPosition(skinMatrices, new Vector3f(0f, 1f, 0f), new int[]{1, 0, 0, 0}, new float[]{1f, 0f, 0f, 0f}, 0, skinned, new Vector3f());

        if (skinned.distance(new Vector3f(0f, 2f, 0f)) > 1e-4f) {
            return "expected skinned position (0,2,0), got " + skinned;
        }
        return null;
    }

    /**
     * The one check in this suite that runs real GL — {@code /sme model selftest} is a
     * client command, so it executes on the render thread with a live context, unlike everything
     * else here. Builds the exact same rig/animation {@link #testSkinningMath} does, runs the real
     * transform-feedback pass ({@link GpuSkinBuffers}), reads the output buffer back with {@code
     * glGetBufferSubData}, and checks it against {@link CpuSkinner}'s own formula for the identical
     * input — the CPU formula is the GPU path's ground truth, so agreement here is a real
     * correctness check, not a smoke test.
     */
    private static String testGpuSkinningMatchesCpu() {
        ModelNode joint0 = new ModelNode(0, "root", new Vector3f(), new Quaternionf(), new Vector3f(1f, 1f, 1f), null, null);
        ModelNode joint1 = new ModelNode(1, "child", new Vector3f(0f, 1f, 0f), new Quaternionf(), new Vector3f(1f, 1f, 1f), null, null);
        joint0.addChild(joint1);

        Skin skin = new Skin(new int[]{0, 1}, new Matrix4f[]{
                new Matrix4f(),
                new Matrix4f().translate(0f, -1f, 0f),
        });

        AnimationData data = new AnimationData();
        data.translation = new Vec3Track(new float[]{0f, 1f},
                new Vector3f[]{new Vector3f(0f, 0f, 0f), new Vector3f(0f, 2f, 0f)}, false);
        Map<Integer, AnimationData> nodes = new LinkedHashMap<>();
        nodes.put(1, data);
        AnimationClip clip = AnimationClip.of("rise", nodes);

        ModelDefinition definition = new ModelDefinition(dummyId(), List.of(joint0), List.of(clip));
        ModelInstance instance = new ModelInstance(definition);
        instance.animator().addLayer(new ClipLayer(ClipAnimationLayerSpec.of("test", clip.name())
                .withPlayMode(AnimationPlayMode.CLAMP_FOREVER)));
        instance.advance(0.5f);

        Matrix4f[] skinMatrices = CpuSkinner.computeSkinMatrices(skin, instance.nodesByIndex(), null);

        int[] joints = {1, 0, 0, 0};
        float[] weights = {1f, 0f, 0f, 0f};
        Vector3f bindPosition = new Vector3f(0f, 1f, 0f);
        Vector3f bindNormal = new Vector3f(0f, 1f, 0f);

        Vector3f cpuPosition = new Vector3f();
        CpuSkinner.skinPosition(skinMatrices, bindPosition, joints, weights, 0, cpuPosition, new Vector3f());
        Vector3f cpuNormal = new Vector3f();
        CpuSkinner.skinNormal(skinMatrices, bindNormal, joints, weights, 0, cpuNormal, new Vector3f());

        VertexData vertices = new VertexData(
                new float[]{bindPosition.x, bindPosition.y, bindPosition.z},
                new float[]{bindNormal.x, bindNormal.y, bindNormal.z},
                new float[0], new float[0], new float[0], joints, weights, 1);
        Primitive primitive = new Primitive(vertices, new int[]{0}, MaterialData.untextured(), List.of());

        GpuSkinBuffers buffers = new GpuSkinBuffers(primitive, skin);
        try {
            // Identity pose: the shader's own pose multiply is a no-op, so its output is directly
            // comparable to CpuSkinner's un-posed result above.
            buffers.updateAndSkin(skinMatrices, null, new Matrix4f(), new Matrix3f());

            Vector3f gpuPosition = readVec3(buffers.outPositionBufferId());
            Vector3f gpuNormal = readVec3(buffers.outNormalBufferId());

            if (gpuPosition.distance(cpuPosition) > 1e-3f) {
                return "GPU-skinned position " + gpuPosition + " disagrees with CPU's " + cpuPosition;
            }
            if (gpuNormal.distance(cpuNormal) > 1e-3f) {
                return "GPU-skinned normal " + gpuNormal + " disagrees with CPU's " + cpuNormal;
            }
        } finally {
            buffers.destroy();
        }
        return null;
    }

    /**
     * The second (and last) check in this suite that runs real GL — same reasoning as {@link
     * #testGpuSkinningMatchesCpu}. Builds a tiny synthetic texture through the real upload path and
     * confirms {@code glGenerateMipmap} actually populated mip level 1 (a nonzero width there is only
     * possible if the whole chain was built from level 0). Deliberately does <b>not</b> assert that
     * the driver actually compressed the texture ({@code GL_TEXTURE_COMPRESSED}) — the GL spec never
     * guarantees a generic compressed internalformat is honored, that's real driver-dependent
     * behavior outside this code's control, so asserting on it would make this test flaky rather than
     * meaningful.
     */
    private static String testModelTextureGeneratesMipmaps() {
        NativeImage image = new NativeImage(8, 8, false);
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                image.setPixelRGBA(x, y, 0xFFFFFFFF);
            }
        }
        ModelTexture texture = new ModelTexture(image, true); // closes `image` itself once uploaded
        try {
            texture.bind();
            int mipWidth = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 1, GL11.GL_TEXTURE_WIDTH);
            if (mipWidth <= 0) {
                return "expected glGenerateMipmap to have populated mip level 1 (an 8x8 base should yield at least a 4x4 level 1), got width " + mipWidth;
            }
        } finally {
            texture.close();
        }
        return null;
    }

    /** Neither Iris nor Oculus is a dependency of this project (by design — see {@code IrisCompat}'s own doc), so this JVM never has {@code net.irisshaders.iris.api.v0.IrisApi} on its classpath — confirms the reflective lookup fails safe to {@code false} rather than throwing. */
    private static String testIrisCompatFailsSafeWhenAbsent() {
        boolean active = IrisCompat.isShaderPackActive();
        if (active) {
            return "expected isShaderPackActive() to be false with no Iris/Oculus on the classpath, got true";
        }
        return null;
    }

    // ---- CUBICSPLINE ----

    private static String testCubicSplineTrackMatchesHermiteFormula() {
        float[] times = {0f, 2f};
        Vector3f[] values = {new Vector3f(0f, 0f, 0f), new Vector3f(10f, 0f, 0f)};
        Vector3f[] inTangents = {new Vector3f(), new Vector3f()};
        Vector3f[] outTangents = {new Vector3f(8f, 0f, 0f), new Vector3f()};
        Vec3Track track = new Vec3Track(times, values, inTangents, outTangents, TrackInterpolation.CUBICSPLINE);

        Vector3f atStart = track.sample(0f);
        if (atStart.distance(values[0]) > 1e-4f) {
            return "expected sample(0) to clamp to the first keyframe's value, got " + atStart;
        }
        Vector3f atEnd = track.sample(2f);
        if (atEnd.distance(values[1]) > 1e-4f) {
            return "expected sample(2) to clamp to the last keyframe's value, got " + atEnd;
        }
        // Hand-computed from the glTF cubic-spline formula (spec Appendix C) at s=0.5 (t=1, dt=2):
        // h00=0.5, h10=0.125, h01=0.5, h11=-0.125 -> x = 0.5*0 + 2*0.125*8 + 0.5*10 + 2*(-0.125)*0
        // = 2 + 5 = 7. Tangents are deliberately asymmetric (8 vs 0) so this fails if the
        // implementation silently fell back to a plain lerp — linear interpolation at the same point
        // would give 5, not 7.
        Vector3f mid = track.sample(1f);
        if (mid.distance(new Vector3f(7f, 0f, 0f)) > 1e-4f) {
            return "expected the Hermite-blended midpoint (7,0,0), got " + mid;
        }
        return null;
    }

    /** The one property specific to quaternions that {@link #testCubicSplineTrackMatchesHermiteFormula} can't cover — the shared Hermite math is already proven there. */
    private static String testCubicSplineQuatTrackNormalizesResult() {
        float[] times = {0f, 1f};
        Quaternionf[] values = {new Quaternionf(), new Quaternionf().rotateY((float) Math.PI / 2f)};
        Quaternionf[] inTangents = {new Quaternionf(0, 0, 0, 0), new Quaternionf(0, 0, 0, 0)};
        Quaternionf[] outTangents = {new Quaternionf(0, 0, 0, 0), new Quaternionf(0, 0, 0, 0)};
        QuatTrack track = new QuatTrack(times, values, inTangents, outTangents, TrackInterpolation.CUBICSPLINE);

        Quaternionf mid = track.sample(0.5f);
        float lengthSq = mid.x * mid.x + mid.y * mid.y + mid.z * mid.z + mid.w * mid.w;
        if (Math.abs(lengthSq - 1f) > 1e-3f) {
            return "expected the cubic-spline quaternion sample to be normalized, got squared length " + lengthSq;
        }
        return null;
    }

    private static Vector3f readVec3(int bufferId) {
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, bufferId);
        FloatBuffer readBack = ByteBuffer.allocateDirect(12).order(ByteOrder.nativeOrder()).asFloatBuffer();
        GL15.glGetBufferSubData(GL15.GL_ARRAY_BUFFER, 0L, readBack);
        return new Vector3f(readBack.get(0), readBack.get(1), readBack.get(2));
    }

    /**
     * The instanced draw path folds a primitive's node transform and the entity's own pose into one
     * per-instance matrix (see {@code ModelRenderer#submitInstanced}), since the instanced shader has
     * only one per-instance model matrix to work with — unlike the CPU path, which applies the same
     * two transforms as two separate steps. This drives the real {@code ModelRenderer.render()} entry
     * point (an unskinned, unmorphed primitive routes to the instanced collector, never a {@code
     * VertexConsumer}) and checks the fold against doing those two steps in sequence — the one place a
     * multiplication-order mistake here would silently misplace every instanced mesh in the game.
     */
    private static String testInstancedSubmissionCombinesNodeAndPose() {
        Vector3f nodeTranslation = new Vector3f(2f, 0f, 0f);
        float[] positions = {0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f};
        VertexData vertices = new VertexData(positions, new float[0], new float[0], new float[0], new float[0], new int[0], new float[0], 3);
        Primitive primitive = new Primitive(vertices, new int[]{0, 1, 2}, MaterialData.untextured(), List.of());
        ModelNode node = new ModelNode(0, "prop", nodeTranslation, new Quaternionf(), new Vector3f(1f, 1f, 1f),
                new Mesh("prop", List.of(primitive)), null);
        ModelDefinition definition = new ModelDefinition(dummyId(), List.of(node), List.of());
        ModelInstance instance = new ModelInstance(definition);
        instance.pose();

        PoseStack poseStack = new PoseStack();
        poseStack.translate(0.0, 5.0, 0.0);

        InstanceBatchCollector.drainAndClear(); // discard anything an earlier test left queued
        new com.dimalab.storymodengine.client.model.ModelRenderer()
                .render(instance, poseStack, null, 0xF000F0, 0, "", Map.of());

        Map<Primitive, InstanceBatchCollector.Batch> batches = InstanceBatchCollector.drainAndClear();
        if (batches.size() != 1) {
            return "expected exactly one primitive submitted for instancing, got " + batches.size();
        }
        InstanceBatchCollector.InstanceData instanceData = batches.values().iterator().next().data();
        if (instanceData.count() != 1) {
            return "expected exactly one submission, got " + instanceData.count();
        }

        Matrix4f combinedModelView = new Matrix4f().set(instanceData.matrixData());
        Vector3f viaCombined = combinedModelView.transformPosition(new Vector3f(0f, 0f, 0f));

        Matrix4f nodeMatrix = new Matrix4f(instance.roots().get(0).globalMatrix());
        Vector3f viaSequential = new Vector3f(0f, 0f, 0f);
        nodeMatrix.transformPosition(viaSequential);
        poseStack.last().pose().transformPosition(viaSequential);

        if (viaCombined.distance(viaSequential) > 1e-4f) {
            return "combined instance transform " + viaCombined + " disagrees with the sequential node-then-pose transform " + viaSequential;
        }
        if (viaSequential.distance(new Vector3f(2f, 5f, 0f)) > 1e-4f) {
            return "sanity check failed: node(+2,0,0) then pose(+0,5,0) should land at (2,5,0), got " + viaSequential;
        }
        return null;
    }

    private static Primitive syntheticPrimitive(int vertexCount, int indexCount) {
        VertexData vertices = new VertexData(new float[0], new float[0], new float[0], new float[0], new float[0],
                new int[0], new float[0], vertexCount);
        return new Primitive(vertices, new int[indexCount], MaterialData.untextured(), List.of());
    }

    /**
     * {@link ModelDefinition#renderPath()}'s three-rule formula, checked on both sides of each rule's
     * own boundary — each case is built to isolate the rule under test (e.g. the rule-2 cases keep
     * {@code totalCubes} below rule 3's 128 floor so rule 3 can't fire and mask a rule-2 regression).
     */
    private static String testRenderPathHeuristic() {
        Object[][] cases = {
                {47, 100, RenderPath.PIPELINE}, {48, 100, RenderPath.BATCHING},
                {24, 4, RenderPath.BATCHING}, {24, 5, RenderPath.PIPELINE},
                {16, 8, RenderPath.BATCHING}, {16, 7, RenderPath.PIPELINE},
                {20, 8, RenderPath.BATCHING}, {20, 9, RenderPath.PIPELINE},
        };
        for (Object[] c : cases) {
            int primitiveCount = (int) c[0];
            int cubesPerPrimitive = (int) c[1];
            RenderPath expected = (RenderPath) c[2];
            ModelDefinition definition = syntheticRenderPathModel(primitiveCount, cubesPerPrimitive);
            RenderPath actual = definition.renderPath();
            if (actual != expected) {
                return "primitiveCount=" + primitiveCount + " cubesPerPrimitive=" + cubesPerPrimitive
                        + ": expected " + expected + ", got " + actual;
            }
        }
        return null;
    }

    private static ModelDefinition syntheticRenderPathModel(int primitiveCount, int cubesPerPrimitive) {
        List<ModelNode> roots = new ArrayList<>();
        for (int i = 0; i < primitiveCount; i++) {
            roots.add(cubeCountNode(i, cubesPerPrimitive));
        }
        return new ModelDefinition(dummyId(), roots, List.of());
    }

    private static ModelNode cubeCountNode(int index, int cubes) {
        VertexData vertices = new VertexData(new float[0], new float[0], new float[0], new float[0], new float[0],
                new int[0], new float[0], cubes * 24);
        Primitive primitive = new Primitive(vertices, new int[cubes * 36], MaterialData.untextured(), List.of());
        String name = "cube" + index;
        return new ModelNode(index, name, new Vector3f(), new Quaternionf(), new Vector3f(1f, 1f, 1f),
                new Mesh(name, List.of(primitive)), null);
    }

    /**
     * A triangle must reach the buffer as exactly three vertices, in order, with its own UVs.
     *
     * <p>This replaced a check for four (a degenerate quad, the third corner repeated), which the
     * renderer needed while it drew through a vanilla {@code Mode.QUADS} entity render type. Now that
     * geometry goes through a real {@code Mode.TRIANGLES} type, padding would submit a third more
     * vertices than the mesh has — so "three" is the invariant worth locking in.
     */
    private static String testTriangleTopology() {
        float[] positions = {0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f};
        float[] uv = {0f, 0f, 1f, 0f, 0f, 1f};
        VertexData vertices = new VertexData(positions, new float[0], new float[0], uv, new float[0], new int[0], new float[0], 3);
        Primitive primitive = new Primitive(vertices, new int[]{0, 1, 2}, MaterialData.untextured(), List.of());

        RecordingConsumer consumer = new RecordingConsumer();
        new com.dimalab.storymodengine.client.model.ModelRenderer()
                .emitPrimitive(consumer, null, primitive, new float[0], new PoseStack().last().pose(), new PoseStack().last().normal(), 0xF000F0, 0);

        if (consumer.count != 3) {
            return "one triangle must emit exactly 3 vertices, got " + consumer.count;
        }
        if (consumer.x[1] != 1f || consumer.y[2] != 1f) {
            return "vertices came through out of order: got (" + consumer.x[1] + ") and (" + consumer.y[2] + ")";
        }
        if (consumer.u[1] != 1f || consumer.v[2] != 1f) {
            return "each vertex must carry its own UV";
        }
        return null;
    }

    /**
     * A material's texture id must be derived from the model it belongs to, so it is identical every
     * load. The first version handed out {@code model/dynamic_<counter>} in upload order, which
     * changed on every reload and said nothing about which model a texture came from.
     */
    private static String testStableTextureKey() {
        // A ResourceLocation already rejects anything outside [a-z0-9/._-], so the id handed in here
        // is by construction a legal one — the only transformation that has to happen is dropping the
        // file extension so the key names the model rather than the file.
        ResourceLocation model = new ResourceLocation("storymodengine", "storymodengine/models/player.glb");
        ResourceLocation first = MaterialData.textureKeyFor(model, 2);
        ResourceLocation again = MaterialData.textureKeyFor(model, 2);

        if (!first.equals(again)) {
            return "the same model and index must always give the same id, got " + first + " and " + again;
        }
        if (!first.getPath().equals("model/storymodengine/models/player/material_2")) {
            return "id should be derived from the model path with the extension dropped; got " + first;
        }
        if (first.equals(MaterialData.textureKeyFor(model, 3))) {
            return "different material indices must not collide";
        }
        // Two different models must not share a key either.
        ResourceLocation other = new ResourceLocation("storymodengine", "storymodengine/models/guard.glb");
        if (first.equals(MaterialData.textureKeyFor(other, 2))) {
            return "different models must not collide on the same material index";
        }
        return null;
    }

    /**
     * A flat export has no hierarchy, so parts that should move together don't. Rigging must rebuild
     * the tree such that rotating a joint carries every member — including one whose own origin sits
     * away from the joint, which is the case the naive "rotate each node about itself" approach gets
     * wrong (the helmet stays behind while the head turns).
     */
    private static String testRigging() {
        // Two unrelated root nodes: a neck-pivoted part and an accessory a block higher up.
        ModelNode neck = flatNode(0, "neck", new Vector3f(0f, 1f, 0f));
        ModelNode hat = flatNode(1, "hat", new Vector3f(0f, 2f, 0f));
        ModelDefinition flat = new ModelDefinition(dummyId(), List.of(neck, hat), List.of());

        ModelDefinition rigged = ModelRig.apply(flat, List.of(
                RigBone.of("head", null, "neck", "neck", "hat")));

        ModelNode head = null;
        for (ModelNode node : rigged.allNodes()) {
            if ("head".equals(node.name())) {
                head = node;
            }
        }
        if (head == null) {
            return "no 'head' bone was created";
        }
        if (head.children().size() != 2) {
            return "the bone should own both members, has " + head.children().size();
        }
        // The pivot came from the anchor, so the bone sits at the neck and the hat is 1 above it.
        if (Math.abs(head.bindTranslation().y - 1f) > 1e-4f) {
            return "bone should pivot at the anchor's origin (y=1), got " + head.bindTranslation();
        }

        ModelInstance instance = new ModelInstance(rigged);
        Vector3f before = nodeOrigin(instance, "hat");

        // Half a turn about Y must swing the hat to the opposite side of the pivot.
        for (RuntimeNode node : instance.nodesByIndex().values()) {
            if ("head".equals(node.definition().name())) {
                RuntimeNode bone = node;
                instance.poseWith(() -> bone.rotation().rotateZ((float) Math.PI));
            }
        }
        Vector3f after = nodeOrigin(instance, "hat");

        if (before == null || after == null) {
            return "hat node missing from the runtime tree";
        }
        // Rotating 180 degrees about Z around the pivot at y=1 takes the hat from y=2 to y=0.
        if (Math.abs(after.y) > 1e-3f) {
            return "hat should swing to y=0 around the joint, got " + after;
        }
        return null;
    }

    /**
     * The rig must come from the model's own {@code .smemeta}, not from Java. Same two-node model as
     * {@link #testRigging}, but assembled through the real load path with a sidecar — which is what
     * lets a new model be added without touching engine code at all.
     */
    private static String testSidecarMetadata() {
        String sidecar = "{"
                + "\"facing\":\"minecraft\","
                + "\"rig\":[{\"name\":\"head\",\"anchor\":\"tri\",\"members\":[\"tri\"]}]"
                + "}";
        ModelMetadata metadata = ModelMetadataLoader.parse(dummyId(), sidecar.getBytes(StandardCharsets.UTF_8));

        if (metadata.facing() != ModelMetadata.Facing.MINECRAFT) {
            return "facing should parse to MINECRAFT, got " + metadata.facing();
        }
        if (!metadata.hasRig() || metadata.rig().size() != 1) {
            return "expected one rig bone from the sidecar, got " + metadata.rig().size();
        }
        RigBone bone = metadata.rig().get(0);
        if (!bone.name().equals("head") || !bone.anchor().equals("tri") || bone.parent() != null) {
            return "bone fields not parsed correctly: " + bone;
        }

        // And the sidecar must actually reach the loaded model.
        byte[] modelBytes = trianglePositionsGltf(null, null).getBytes(StandardCharsets.UTF_8);
        ModelDefinition withSidecar = ModelLoading.load(dummyId(), modelBytes, sidecar.getBytes(StandardCharsets.UTF_8), null);
        if (withSidecar.nodeByName("head") == null) {
            return "the sidecar's bone did not reach the loaded model";
        }

        // Malformed metadata must cost the model its rig, not its geometry.
        ModelMetadata broken = ModelMetadataLoader.parse(dummyId(), "{ not json".getBytes(StandardCharsets.UTF_8));
        if (broken.hasRig() || broken.facing() != ModelMetadata.Facing.AUTO) {
            return "a malformed sidecar should fall back to empty metadata";
        }
        ModelDefinition stillLoads = ModelLoading.load(dummyId(), modelBytes, "{ not json".getBytes(StandardCharsets.UTF_8), null);
        if (findMeshNode(stillLoads) == null) {
            return "a malformed sidecar must not stop the model itself from loading";
        }
        return null;
    }

    private static ModelNode flatNode(int index, String name, Vector3f translation) {
        VertexData vertices = new VertexData(new float[]{0f, 0f, 0f}, new float[0], new float[0], new float[0], new float[0], new int[0], new float[0], 1);
        Primitive primitive = new Primitive(vertices, new int[]{0}, MaterialData.untextured(), List.of());
        return new ModelNode(index, name, translation, new Quaternionf(), new Vector3f(1f, 1f, 1f),
                new Mesh(name, List.of(primitive)), null);
    }

    private static Vector3f nodeOrigin(ModelInstance instance, String name) {
        for (RuntimeNode node : instance.nodesByIndex().values()) {
            if (name.equals(node.definition().name())) {
                return node.globalMatrix().getTranslation(new Vector3f());
            }
        }
        return null;
    }

    /**
     * Pins the one thing that made the character look the wrong way.
     *
     * <p>Vanilla draws entity models inside a {@code scale(-1, -1, 1)} flip, and every Minecraft angle
     * — including the {@code netHeadYaw} a render layer is handed — is expressed in that space.
     * {@code GltfModelLayer} undoes the flip so Y-up geometry draws correctly, which inverts X and Y
     * rotations ({@code S·R(θ)·S⁻¹ = R(−θ)}) while leaving Z alone. This asserts that identity against
     * vanilla's actual transform chain, so nobody "simplifies away" the negation in
     * {@code HumanoidPoser} and silently mirrors every character again.
     */
    private static String testRotationConvention() {
        for (float degrees : new float[]{30f, 90f, -60f}) {
            float radians = (float) Math.toRadians(degrees);

            String yaw = sameRotation("yaw " + degrees,
                    conjugated(new Matrix4f().rotationY(radians)), new Matrix4f().rotationY(-radians));
            if (yaw != null) {
                return yaw;
            }
            String pitch = sameRotation("pitch " + degrees,
                    conjugated(new Matrix4f().rotationX(radians)), new Matrix4f().rotationX(-radians));
            if (pitch != null) {
                return pitch;
            }
            // Roll survives untouched: its two axes are both negated, so the flip cancels itself.
            String roll = sameRotation("roll " + degrees,
                    conjugated(new Matrix4f().rotationZ(radians)), new Matrix4f().rotationZ(radians));
            if (roll != null) {
                return roll;
            }
        }
        return null;
    }

    /** {@code S · R · S⁻¹} for vanilla's {@code scale(-1, -1, 1)} — which is self-inverse, so S serves as both. */
    private static Matrix4f conjugated(Matrix4f rotation) {
        Matrix4f flip = new Matrix4f().scale(-1f, -1f, 1f);
        return new Matrix4f(flip).mul(rotation).mul(flip);
    }

    private static String sameRotation(String label, Matrix4f expected, Matrix4f actual) {
        Vector3f[] basis = {new Vector3f(1f, 0f, 0f), new Vector3f(0f, 1f, 0f), new Vector3f(0f, 0f, 1f)};
        for (Vector3f axis : basis) {
            Vector3f a = expected.transformDirection(new Vector3f(axis));
            Vector3f b = actual.transformDirection(new Vector3f(axis));
            if (a.distance(b) > 1e-4f) {
                return label + ": conjugated rotation is " + a + " but the angle used gives " + b;
            }
        }
        return null;
    }

    // ---- physics ----

    private static String testBounds() {
        ModelBounds bounds = ModelBounds.of(cubeModel(new Vector3f(0f, 0f, 0f), new Vector3f(1f, 2f, 1f)));
        if (bounds.min().distance(new Vector3f(0f, 0f, 0f)) > 1e-4f) {
            return "min expected (0,0,0), got " + bounds.min();
        }
        if (bounds.max().distance(new Vector3f(1f, 2f, 1f)) > 1e-4f) {
            return "max expected (1,2,1), got " + bounds.max();
        }
        // Vanilla entities get one box: horizontal extent is the larger of X/Z, height is Y.
        EntityDimensions dimensions = bounds.toEntityDimensions();
        if (Math.abs(dimensions.width - 1f) > 1e-4f || Math.abs(dimensions.height - 2f) > 1e-4f) {
            return "entity dimensions expected 1x2, got " + dimensions.width + "x" + dimensions.height;
        }
        return null;
    }

    /**
     * {@link ModelBounds#toWorldCullingBox}: unrotated, the box keeps its own extent, just moved;
     * turned 90°, a 2-wide-by-4-deep footprint becomes 4-wide-by-2-deep (X and Z swap); a pure Y
     * translation shifts the vertical range by exactly that much regardless of rotation.
     */
    private static String testWorldCullingBox() {
        ModelBounds bounds = new ModelBounds(new Vector3f(-1f, 0f, -2f), new Vector3f(1f, 2f, 2f));

        AABB atOrigin = bounds.toWorldCullingBox(0, 0, 0, 0f);
        if (Math.abs(atOrigin.minX + 1) > 1e-3 || Math.abs(atOrigin.maxX - 1) > 1e-3
                || Math.abs(atOrigin.minZ + 2) > 1e-3 || Math.abs(atOrigin.maxZ - 2) > 1e-3) {
            return "unrotated box should keep its own footprint, got " + atOrigin;
        }

        AABB rotated = bounds.toWorldCullingBox(0, 0, 0, 90f);
        double width = rotated.maxX - rotated.minX;
        double depth = rotated.maxZ - rotated.minZ;
        if (Math.abs(width - 4.0) > 1e-2 || Math.abs(depth - 2.0) > 1e-2) {
            return "a 90-degree turn should swap the 2x4 footprint to 4x2, got " + width + "x" + depth;
        }

        AABB moved = bounds.toWorldCullingBox(10, 5, 10, 0f);
        if (Math.abs(moved.minY - 5.0) > 1e-3 || Math.abs(moved.maxY - 7.0) > 1e-3) {
            return "a Y translation should shift the vertical range by exactly that much, got " + moved;
        }
        return null;
    }

    /**
     * {@link ModelInstance#worldCullingBox}: unlike {@link ModelBounds#toWorldCullingBox} above (bind
     * pose only, rotated by yaw), this must track a node's <i>live</i> pose — moved here directly
     * (bypassing a whole posing pass) to simulate an animated reach past the bind pose, e.g. an attack
     * swing, the exact case the class doc calls out as what the old whole-model box couldn't see.
     *
     * <p>Uses its own unique model id rather than the shared {@link #dummyId()} — {@code
     * ModelPhysics#perNodeBounds} caches by id in a static map that outlives this one test, and a
     * shared id would risk another test's definition serving stale per-node bounds here.
     */
    private static String testWorldCullingBoxTracksLivePose() {
        VertexData vertices = new VertexData(new float[]{0f, 0f, 0f}, new float[0], new float[0], new float[0], new float[0], new int[0], new float[0], 1);
        Primitive primitive = new Primitive(vertices, new int[]{0}, MaterialData.untextured(), List.of());
        ModelNode node = new ModelNode(0, "part", new Vector3f(), new Quaternionf(), new Vector3f(1f, 1f, 1f),
                new Mesh("part", List.of(primitive)), null);
        ResourceLocation uniqueId = new ResourceLocation("storymodengine", "selftest/world_culling_box_live_pose");
        ModelDefinition definition = new ModelDefinition(uniqueId, List.of(node), List.of());
        ModelInstance instance = new ModelInstance(definition);

        RuntimeNode runtimeNode = instance.roots().get(0);
        runtimeNode.translation().set(10f, 0f, 0f);
        runtimeNode.updateHierarchy();

        AABB box = instance.worldCullingBox(new Matrix4f());
        if (box == null) {
            return "expected a non-null culling box for a model with one meshed node";
        }
        if (Math.abs(box.minX - 10.0) > 1e-3 || Math.abs(box.maxX - 10.0) > 1e-3) {
            return "expected the live-pose translation (+10 on X, applied after construction, not the bind pose) to be reflected, got " + box;
        }
        return null;
    }

    /** A cube filling its own bounds must voxelize to a shape spanning the whole block. */
    private static String testVoxelizeCube() {
        ShapeDefinition shape = ModelVoxelizer.forBlock(cubeModel(new Vector3f(0f, 0f, 0f), new Vector3f(1f, 1f, 1f)));
        VoxelShape voxel = shape.toVoxelShape();
        if (voxel.isEmpty()) {
            return "a solid cube voxelized to an empty shape";
        }
        AABB box = voxel.bounds();
        if (box.minX > 0.01 || box.minY > 0.01 || box.minZ > 0.01
                || box.maxX < 0.99 || box.maxY < 0.99 || box.maxZ < 0.99) {
            return "expected the shape to span the unit cube, got " + box;
        }
        return null;
    }

    /**
     * A solid 16³ grid is 4096 cells; without greedy merging that becomes 4096 boxes and every
     * collision query against the shape crawls. Each Y layer should collapse to a single box.
     */
    private static String testVoxelMerging() {
        ShapeDefinition shape = ModelVoxelizer.forBlock(cubeModel(new Vector3f(0f, 0f, 0f), new Vector3f(1f, 1f, 1f)));
        int boxes = shape.boxes().size();
        if (boxes > 32) {
            return "expected a solid cube to merge to ~16 boxes (one per layer), got " + boxes;
        }
        return null;
    }

    /** A closed shell must fill solid, or a player could clip inside and stand in the middle of the object. */
    private static String testSolidFill() {
        ModelDefinition cube = cubeModel(new Vector3f(0f, 0f, 0f), new Vector3f(1f, 1f, 1f));

        ShapeDefinition hollow = ModelVoxelizer.forBlock(cube, 16, false);
        ShapeDefinition solid = ModelVoxelizer.forBlock(cube, 16, true);

        if (containsCenter(hollow)) {
            return "with solid=false the interior should stay empty, but the centre was filled";
        }
        if (!containsCenter(solid)) {
            return "with solid=true the interior should be filled, but the centre was empty";
        }
        return null;
    }

    private static boolean containsCenter(ShapeDefinition shape) {
        for (AABB box : shape.toVoxelShape().toAabbs()) {
            if (box.contains(0.5, 0.5, 0.5)) {
                return true;
            }
        }
        return false;
    }

    /**
     * A square footprint turned 45° needs a box √2 times wider to still contain it. Without this the
     * hitbox stayed sized for the unrotated model and the mesh's corners hung outside it — visible
     * the moment {@code F3+B} is switched on.
     */
    private static String testRotatedHitbox() {
        ModelBounds bounds = ModelBounds.of(cubeModel(new Vector3f(0f, 0f, 0f), new Vector3f(1f, 1f, 1f)));

        float straight = bounds.toEntityDimensions(0f).width;
        if (Math.abs(straight - 1f) > 1e-4f) {
            return "unrotated width should be 1, got " + straight;
        }
        float diagonal = bounds.toEntityDimensions(45f).width;
        if (Math.abs(diagonal - (float) Math.sqrt(2)) > 1e-3f) {
            return "at 45 degrees the width should be sqrt(2)=1.414, got " + diagonal;
        }
        // 90 degrees puts a square back on axis, so it must be back to 1.
        float quarter = bounds.toEntityDimensions(90f).width;
        if (Math.abs(quarter - 1f) > 1e-4f) {
            return "at 90 degrees a square footprint should be 1 again, got " + quarter;
        }
        return null;
    }

    /**
     * The block's render rotation and its collision rotation must agree, or the model is drawn facing
     * one way while you collide with it facing another. {@code voxel.ShapeRotation} turns clockwise
     * quarter-steps from NORTH, so the render is −90° per step — deliberately not {@code
     * Direction.toYRot()}, which disagrees for every direction and was the original bug.
     */
    private static String testBlockRotationAgreement() {
        record Case(Direction facing, float expected) {
        }
        Case[] cases = {
                new Case(Direction.NORTH, 0f),
                new Case(Direction.EAST, -90f),
                new Case(Direction.SOUTH, -180f),
                new Case(Direction.WEST, -270f),
        };
        for (Case testCase : cases) {
            float actual = ModelRotation.renderYawFor(testCase.facing());
            if (Math.abs(actual - testCase.expected()) > 1e-4f) {
                return testCase.facing() + " should render at " + testCase.expected() + " degrees, got " + actual;
            }
        }
        // And the shape must actually move with it: a box on the north edge lands on the east edge
        // after one clockwise step, which is what the render angle above is compensating for.
        ShapeDefinition northStrip = ShapeDefinition.builder().box(0, 0, 0, 16, 16, 4).build();
        AABB rotated = northStrip.rotated(Direction.EAST).toVoxelShape().bounds();
        if (rotated.minX < 0.74 || rotated.maxX < 0.99) {
            return "a north-edge shape should rotate to the east edge, got " + rotated;
        }
        return null;
    }

    /** A closed axis-aligned box mesh: 8 corners, 12 triangles, wound outward. */
    private static ModelDefinition cubeModel(Vector3f min, Vector3f max) {
        float[] positions = {
                min.x, min.y, min.z,  max.x, min.y, min.z,  max.x, max.y, min.z,  min.x, max.y, min.z,
                min.x, min.y, max.z,  max.x, min.y, max.z,  max.x, max.y, max.z,  min.x, max.y, max.z,
        };
        int[] indices = {
                0, 2, 1, 0, 3, 2,   // -Z
                4, 5, 6, 4, 6, 7,   // +Z
                0, 7, 3, 0, 4, 7,   // -X
                1, 2, 6, 1, 6, 5,   // +X
                0, 1, 5, 0, 5, 4,   // -Y
                3, 7, 6, 3, 6, 2,   // +Y
        };
        VertexData vertices = new VertexData(positions, new float[0], new float[0], new float[0], new float[0], new int[0], new float[0], 8);
        Primitive primitive = new Primitive(vertices, indices, MaterialData.untextured(), List.of());
        ModelNode node = new ModelNode(0, "cube", new Vector3f(), new Quaternionf(), new Vector3f(1f, 1f, 1f),
                new Mesh("cube", List.of(primitive)), null);
        return new ModelDefinition(dummyId(), List.of(node), List.of());
    }

    /** Records what a render pass would have written, so vertex topology can be asserted with no GL context. */
    private static final class RecordingConsumer implements VertexConsumer {
        private final float[] x = new float[64];
        private final float[] y = new float[64];
        private final float[] z = new float[64];
        private final float[] u = new float[64];
        private final float[] v = new float[64];
        private int count;
        private float pendingX;
        private float pendingY;
        private float pendingZ;
        private float pendingU;
        private float pendingV;

        @Override
        public VertexConsumer vertex(double px, double py, double pz) {
            pendingX = (float) px;
            pendingY = (float) py;
            pendingZ = (float) pz;
            return this;
        }

        @Override
        public VertexConsumer color(int r, int g, int b, int a) {
            return this;
        }

        @Override
        public VertexConsumer uv(float pu, float pv) {
            pendingU = pu;
            pendingV = pv;
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int a, int b) {
            return this;
        }

        @Override
        public VertexConsumer uv2(int a, int b) {
            return this;
        }

        @Override
        public VertexConsumer normal(float nx, float ny, float nz) {
            return this;
        }

        @Override
        public void endVertex() {
            if (count < x.length) {
                x[count] = pendingX;
                y[count] = pendingY;
                z[count] = pendingZ;
                u[count] = pendingU;
                v[count] = pendingV;
            }
            count++;
        }

        @Override
        public void defaultColor(int r, int g, int b, int a) {
        }

        @Override
        public void unsetDefaultColor() {
        }
    }

    // ---- helpers ----

    private static AnimationClip clipTranslating(int nodeIndex, Vector3f delta) {
        AnimationData data = new AnimationData();
        data.translation = new Vec3Track(new float[]{0f, 1f}, new Vector3f[]{new Vector3f(delta), new Vector3f(delta)}, false);
        Map<Integer, AnimationData> nodes = new LinkedHashMap<>();
        nodes.put(nodeIndex, data);
        return AnimationClip.of("move", nodes);
    }

    private static ModelNode findMeshNode(ModelDefinition definition) {
        for (ModelNode node : definition.allNodes()) {
            if (node.mesh() != null) {
                return node;
            }
        }
        return null;
    }

    private static String trianglePositionsGltf(String generator, String extra) {
        float[] positions = {0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f};
        return "{"
                + "\"asset\":{\"version\":\"2.0\",\"generator\":\"" + (generator == null ? "Blockbench" : generator) + "\"},"
                + "\"buffers\":[" + floatBuffer(positions) + "],"
                + "\"bufferViews\":[{\"buffer\":0,\"byteOffset\":0,\"byteLength\":" + (positions.length * 4) + "}],"
                + "\"accessors\":[{\"bufferView\":0,\"componentType\":5126,\"count\":3,\"type\":\"VEC3\"}],"
                + "\"meshes\":[{\"primitives\":[{\"attributes\":{\"POSITION\":0}}]}],"
                + "\"nodes\":[{\"name\":\"tri\",\"mesh\":0}],"
                + "\"scenes\":[{\"nodes\":[0]}],\"scene\":0"
                + (extra == null ? "" : "," + extra)
                + "}";
    }

    private static String floatBuffer(float[] data) {
        ByteBuffer buffer = ByteBuffer.allocate(data.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float f : data) {
            buffer.putFloat(f);
        }
        String base64 = Base64.getEncoder().encodeToString(buffer.array());
        return "{\"uri\":\"data:application/octet-stream;base64," + base64 + "\",\"byteLength\":" + buffer.capacity() + "}";
    }

    private static float[] concat(float[] a, float[] b) {
        float[] out = new float[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private static boolean nearlyEqual(float[] a, float[] b) {
        if (a.length != b.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (Math.abs(a[i] - b[i]) > 1e-5f) {
                return false;
            }
        }
        return true;
    }

    private static ResourceLocation dummyId() {
        return new ResourceLocation("storymodengine", "selftest/dummy");
    }

    // ---- animation expression language ----

    private static String testExpressionArithmeticPrecedence() {
        float a = AnimExpr.evalFloat(AnimationExpression.of("1 + 2 * 3"), new AnimEvalContext(), -1f);
        if (a != 7f) {
            return "'1 + 2 * 3' should be 7 (multiplication before addition), got " + a;
        }
        float b = AnimExpr.evalFloat(AnimationExpression.of("(1 + 2) * 3"), new AnimEvalContext(), -1f);
        if (b != 9f) {
            return "'(1 + 2) * 3' should be 9, got " + b;
        }
        return null;
    }

    private static String testExpressionComparisonAndBoolean() {
        boolean a = AnimExpr.evalBool(AnimationExpression.of("1 < 2 && 2 <= 2"), new AnimEvalContext(), false);
        if (!a) {
            return "'1 < 2 && 2 <= 2' should be true";
        }
        boolean b = AnimExpr.evalBool(AnimationExpression.of("!(1 == 1)"), new AnimEvalContext(), true);
        if (b) {
            return "'!(1 == 1)' should be false";
        }
        return null;
    }

    private static String testExpressionTernary() {
        float value = AnimExpr.evalFloat(AnimationExpression.of("1 < 2 ? 5 : 9"), new AnimEvalContext(), -1f);
        if (value != 5f) {
            return "'1 < 2 ? 5 : 9' should be 5, got " + value;
        }
        return null;
    }

    private static String testExpressionNamespaces() {
        AnimEvalContext context = new AnimEvalContext();
        context.variables.put("foo", 3f);
        context.temporaries.put("foo", 4f);
        context.data = java.util.Map.of("foo", 5f);

        float v = AnimExpr.evalFloat(AnimationExpression.of("variable.foo"), context, -1f);
        if (v != 3f) {
            return "variable.foo should read from the variables map, got " + v;
        }
        float t = AnimExpr.evalFloat(AnimationExpression.of("temp.foo"), context, -1f);
        if (t != 4f) {
            return "temp.foo should read from the temporaries map, got " + t;
        }
        float d = AnimExpr.evalFloat(AnimationExpression.of("data.foo"), context, -1f);
        if (d != 5f) {
            return "data.foo should read from the data map, got " + d;
        }

        // No entity: an entity-derived query falls back to a same-named variable.
        context.variables.put("is_on_ground", 1f);
        float q = AnimExpr.evalFloat(AnimationExpression.of("query.is_on_ground"), context, -1f);
        if (q != 1f) {
            return "query.is_on_ground with no entity should fall back to variables.is_on_ground, got " + q;
        }
        return null;
    }

    private static String testExpressionCaching() {
        String source = "this is not a valid expression (((";
        AnimExpr.warnedSources().remove(source);
        AnimEvalContext context = new AnimEvalContext();

        float first = AnimExpr.evalFloat(AnimationExpression.of(source), context, -1f);
        float second = AnimExpr.evalFloat(AnimationExpression.of(source), context, -1f);
        if (first != 0f || second != 0f) {
            return "a malformed expression should evaluate to 0 (fail-safe), got " + first + " and " + second;
        }
        if (!AnimExpr.warnedSources().contains(source)) {
            return "a malformed expression should be recorded as warned-about exactly once";
        }
        return null;
    }

    private static String testExpressionFunctionCalls() {
        AnimEvalContext context = new AnimEvalContext();
        float clampedLow = AnimExpr.evalFloat(AnimationExpression.of("clamp(-5, 0, 10)"), context, -1f);
        if (clampedLow != 0f) {
            return "clamp(-5, 0, 10) should be 0, got " + clampedLow;
        }
        float clampedHigh = AnimExpr.evalFloat(AnimationExpression.of("clamp(15, 0, 10)"), context, -1f);
        if (clampedHigh != 10f) {
            return "clamp(15, 0, 10) should be 10, got " + clampedHigh;
        }
        float clampedMiddle = AnimExpr.evalFloat(AnimationExpression.of("clamp(5, 0, 10)"), context, -1f);
        if (clampedMiddle != 5f) {
            return "clamp(5, 0, 10) should be 5, got " + clampedMiddle;
        }
        float min = AnimExpr.evalFloat(AnimationExpression.of("min(3, 7)"), context, -1f);
        float max = AnimExpr.evalFloat(AnimationExpression.of("max(3, 7)"), context, -1f);
        if (min != 3f || max != 7f) {
            return "min(3,7)/max(3,7) should be 3/7, got " + min + "/" + max;
        }
        float abs = AnimExpr.evalFloat(AnimationExpression.of("abs(-4)"), context, -1f);
        if (abs != 4f) {
            return "abs(-4) should be 4, got " + abs;
        }
        // Wrong arity must degrade to 0 rather than throw — this runs every frame, on live game data.
        float wrongArity = AnimExpr.evalFloat(AnimationExpression.of("clamp(1, 2)"), context, -1f);
        if (wrongArity != 0f) {
            return "a function call with the wrong number of arguments should fall back to 0, got " + wrongArity;
        }
        return null;
    }

    private static String testExpressionBareIdentifierIsQuery() {
        AnimEvalContext context = new AnimEvalContext();
        context.variables.put("is_on_ground", 1f);
        // No entity — falls back to the same-named variable, exactly like an explicit
        // "query.is_on_ground" already does (testExpressionNamespaces) — the point of this test is
        // that the bare form now resolves through that identical path, not a separate one that just
        // happens to agree.
        float bare = AnimExpr.evalFloat(AnimationExpression.of("is_on_ground"), context, -1f);
        if (bare != 1f) {
            return "a bare identifier should resolve as an implicit query.* lookup, got " + bare;
        }
        return null;
    }

    private static String testExpressionPiConstant() {
        AnimEvalContext context = new AnimEvalContext();
        float pi = AnimExpr.evalFloat(AnimationExpression.of("pi"), context, -1f);
        if (Math.abs(pi - (float) Math.PI) > 1e-5f) {
            return "'pi' should evaluate to Math.PI, got " + pi;
        }
        return null;
    }

    private static String testExpressionMathFunctions() {
        AnimEvalContext context = new AnimEvalContext();
        float sin90 = AnimExpr.evalFloat(AnimationExpression.of("sin(90)"), context, -1f);
        if (Math.abs(sin90 - 1f) > 1e-4f) {
            return "sin(90) (degrees) should be 1, got " + sin90;
        }
        float cos0 = AnimExpr.evalFloat(AnimationExpression.of("cos(0)"), context, -1f);
        if (Math.abs(cos0 - 1f) > 1e-4f) {
            return "cos(0) should be 1, got " + cos0;
        }
        float sqrt = AnimExpr.evalFloat(AnimationExpression.of("sqrt(16)"), context, -1f);
        if (sqrt != 4f) {
            return "sqrt(16) should be 4, got " + sqrt;
        }
        float floor = AnimExpr.evalFloat(AnimationExpression.of("floor(1.9)"), context, -1f);
        if (floor != 1f) {
            return "floor(1.9) should be 1, got " + floor;
        }
        float ceil = AnimExpr.evalFloat(AnimationExpression.of("ceil(1.1)"), context, -1f);
        if (ceil != 2f) {
            return "ceil(1.1) should be 2, got " + ceil;
        }
        float pow = AnimExpr.evalFloat(AnimationExpression.of("pow(2, 3)"), context, -1f);
        if (pow != 8f) {
            return "pow(2, 3) should be 8, got " + pow;
        }
        // True modulo (always the sign of the divisor), not Java's remainder operator.
        float mod = AnimExpr.evalFloat(AnimationExpression.of("mod(-1, 4)"), context, -1f);
        if (Math.abs(mod - 3f) > 1e-4f) {
            return "mod(-1, 4) should be a true modulo (3), got " + mod;
        }
        float atan2 = AnimExpr.evalFloat(AnimationExpression.of("atan2(1, 1)"), context, -1f);
        if (Math.abs(atan2 - 45f) > 1e-3f) {
            return "atan2(1, 1) should be 45 degrees, got " + atan2;
        }
        return null;
    }

    private static String testExpressionNewQueryFields() {
        AnimEvalContext context = new AnimEvalContext();
        context.variables.put("move_dist", 2.5f);
        context.variables.put("air_supply", 250f);
        context.variables.put("speed", 0.1f);
        float moveDist = AnimExpr.evalFloat(AnimationExpression.of("query.move_dist"), context, -1f);
        if (moveDist != 2.5f) {
            return "query.move_dist should fall back to variables.move_dist with no entity, got " + moveDist;
        }
        float airSupply = AnimExpr.evalFloat(AnimationExpression.of("query.air_supply"), context, -1f);
        if (airSupply != 250f) {
            return "query.air_supply should fall back to variables.air_supply with no entity, got " + airSupply;
        }
        float speed = AnimExpr.evalFloat(AnimationExpression.of("query.speed"), context, -1f);
        if (speed != 0.1f) {
            return "query.speed should fall back to variables.speed with no entity, got " + speed;
        }
        return null;
    }

    // ---- standard player animator preset (HollowEngine-identical default NPC) ----

    private static String testStandardPlayerPresetRegistration() {
        List<AnimatorLayerSpec> specs = AnimatorPresets.get(StandardPlayerAnimatorPreset.ID);
        if (specs.size() != 3) {
            return "expected 3 layer specs (locomotion/look_procedural/face_angry) from the standard player preset, got " + specs.size();
        }
        if (!(specs.get(0) instanceof AnimationControllerLayerSpec locomotion)) {
            return "first spec should be the locomotion AnimationControllerLayerSpec, got " + specs.get(0).getClass();
        }
        if (locomotion.states().size() != 7) {
            return "expected 7 locomotion states (death/attack/sneak/run/levitation/walk/idle), got " + locomotion.states().size();
        }
        if (locomotion.transitions().size() != 7) {
            return "expected 7 locomotion transitions, got " + locomotion.transitions().size();
        }
        for (AnimationControllerTransitionSpec transition : locomotion.transitions()) {
            if (!AnimationControllerTransitionSpec.ANY_STATE.equals(transition.from())) {
                return "every locomotion transition should originate from ANY_STATE, found from=" + transition.from();
            }
        }
        if (!"idle".equals(locomotion.entryState())) {
            return "locomotion entry state should be 'idle', got " + locomotion.entryState();
        }

        if (!(specs.get(1) instanceof ProceduralLayerSpec look)) {
            return "second spec should be the look ProceduralLayerSpec, got " + specs.get(1).getClass();
        }
        if (look.transforms().size() != 3) {
            return "expected 3 procedural bone transforms (Head/LeftEye/RightEye), got " + look.transforms().size();
        }
        if (look.blendMode() != LayerBlendMode.ADDITIVE) {
            return "the look-procedural layer should blend additively, got " + look.blendMode();
        }

        // Unknown preset id must degrade to an empty list rather than throw.
        if (!AnimatorPresets.get("storymodengine:does_not_exist").isEmpty()) {
            return "an unknown preset id should resolve to an empty layer list";
        }
        return null;
    }

    /**
     * Structure alone (the previous test) can't catch a condition string that parses fine but always
     * evaluates false — exactly what happened here once: every transition condition was ported from
     * HollowEngine's Kotlin source with bare names ({@code is_alive}, {@code horizontal_speed}), which
     * in <i>this</i> grammar is a reserved bare identifier that silently evaluates to 0 rather than a
     * query lookup (unlike HE's own dialect) — so {@code is_alive != 0.0} was always false, no
     * transition ever fired, and the NPC was stuck on its entry state forever. This test actually
     * evaluates the real conditions against a fake context, which the structural test cannot.
     */
    private static String testStandardPlayerControllerConditionsEvaluate() {
        AnimationControllerLayerSpec locomotion = (AnimationControllerLayerSpec)
                AnimatorPresets.get(StandardPlayerAnimatorPreset.ID).get(0);
        AnimationControllerTransitionSpec walk = findTransitionTo(locomotion, "walk");
        AnimationControllerTransitionSpec idle = findTransitionTo(locomotion, "idle");
        if (walk == null || idle == null) {
            return "expected both a 'walk' and an 'idle' transition in the locomotion layer";
        }

        AnimEvalContext context = new AnimEvalContext();
        context.variables.put("is_alive", 1f); // is_alive is entity-derived; with no entity it falls back to this
        context.horizontalSpeed = 0f;
        if (!AnimExpr.evalBool(idle.condition(), context, false)) {
            return "idle's condition should be true while alive and not moving";
        }
        if (AnimExpr.evalBool(walk.condition(), context, false)) {
            return "walk's condition should be false while not moving";
        }

        context.horizontalSpeed = 1f;
        if (!AnimExpr.evalBool(walk.condition(), context, false)) {
            return "walk's condition should be true while alive and moving — if this fails, a bare "
                    + "query name in the expression text is silently evaluating to 0 again";
        }
        if (AnimExpr.evalBool(idle.condition(), context, false)) {
            return "idle's condition should be false while moving";
        }
        return null;
    }

    /**
     * The attack state/transition added to fix the gap the earlier HE comparison found: the model
     * always shipped an "attack" clip, but nothing in the controller ever selected it.
     */
    private static String testStandardPlayerAttackTransition() {
        AnimationControllerLayerSpec locomotion = (AnimationControllerLayerSpec)
                AnimatorPresets.get(StandardPlayerAnimatorPreset.ID).get(0);
        AnimationControllerTransitionSpec attack = findTransitionTo(locomotion, "attack");
        if (attack == null) {
            return "expected an 'attack' transition in the locomotion layer";
        }
        AnimationControllerTransitionSpec sneak = findTransitionTo(locomotion, "sneak");
        if (sneak != null && attack.priority() <= sneak.priority()) {
            return "attack should outrank movement states like sneak so a swing reads even mid-action, got priority "
                    + attack.priority() + " vs sneak's " + sneak.priority();
        }
        AnimationControllerTransitionSpec death = findTransitionTo(locomotion, "death");
        if (death != null && attack.priority() >= death.priority()) {
            return "death should still outrank attack, got priority " + attack.priority() + " vs death's " + death.priority();
        }

        AnimEvalContext context = new AnimEvalContext();
        context.variables.put("is_alive", 1f);
        context.variables.put("is_swinging", 0f);
        if (AnimExpr.evalBool(attack.condition(), context, false)) {
            return "attack's condition should be false while not swinging";
        }
        context.variables.put("is_swinging", 1f);
        if (!AnimExpr.evalBool(attack.condition(), context, false)) {
            return "attack's condition should be true while swinging and alive";
        }

        boolean hasAttackState = false;
        for (AnimationControllerStateSpec state : locomotion.states()) {
            if ("attack".equals(state.id())) {
                hasAttackState = true;
                if (!"attack".equals(state.animation())) {
                    return "the attack state should play the 'attack' clip, got " + state.animation();
                }
                if (state.playMode() != AnimationPlayMode.ONCE) {
                    return "the attack state should play once, not loop, got " + state.playMode();
                }
            }
        }
        if (!hasAttackState) {
            return "expected an 'attack' state in the locomotion layer";
        }
        return null;
    }

    /** The angry-face layer added to hold steady for a whole engagement, not flicker on/off with each individual attack pulse. */
    private static String testStandardPlayerAngryFaceLayer() {
        List<AnimatorLayerSpec> specs = AnimatorPresets.get(StandardPlayerAnimatorPreset.ID);
        if (specs.size() != 3 || !(specs.get(2) instanceof ClipAnimationLayerSpec face)) {
            return "expected the third preset layer to be the face_angry ClipAnimationLayerSpec";
        }
        if (!"face-angry".equals(face.animation())) {
            return "expected the angry-face layer to play the 'face-angry' clip, got " + face.animation();
        }
        if (face.blendMode() != LayerBlendMode.ADDITIVE) {
            return "the angry-face layer should blend additively so it doesn't clobber the body pose, got " + face.blendMode();
        }
        for (String bone : List.of("LeftUp", "RightUp", "LeftBrow", "RightBrow", "Brows")) {
            if (!face.mask().includes().contains(bone)) {
                return "expected the angry-face mask to include '" + bone + "', got " + face.mask().includes();
            }
        }

        AnimEvalContext context = new AnimEvalContext();
        context.variables.put("is_alive", 1f);
        context.variables.put("has_target", 0f);
        if (AnimExpr.evalFloat(face.weight(), context, 0f) > 0f) {
            return "the angry-face layer's weight should be 0 without a target";
        }
        context.variables.put("has_target", 1f);
        if (AnimExpr.evalFloat(face.weight(), context, 0f) <= 0f) {
            return "the angry-face layer's weight should be > 0 for the whole time NpcEntity#hasTarget is true, not just mid-swing or mid-chase";
        }
        return null;
    }

    private static AnimationControllerTransitionSpec findTransitionTo(AnimationControllerLayerSpec spec, String to) {
        for (AnimationControllerTransitionSpec transition : spec.transitions()) {
            if (transition.to().equals(to)) {
                return transition;
            }
        }
        return null;
    }

    private static String testSkinMaterialOverride() {
        float[] positions = {0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f};
        String json = "{"
                + "\"asset\":{\"version\":\"2.0\",\"generator\":\"Blockbench\"},"
                + "\"buffers\":[" + floatBuffer(positions) + "],"
                + "\"bufferViews\":[{\"buffer\":0,\"byteOffset\":0,\"byteLength\":" + (positions.length * 4) + "}],"
                + "\"accessors\":[{\"bufferView\":0,\"componentType\":5126,\"count\":3,\"type\":\"VEC3\"}],"
                + "\"materials\":[{},{}],"
                + "\"meshes\":[{\"primitives\":[{\"attributes\":{\"POSITION\":0},\"material\":1}]}],"
                + "\"nodes\":[{\"name\":\"tri\",\"mesh\":0}],"
                + "\"scenes\":[{\"nodes\":[0]}],\"scene\":0"
                + "}";
        byte[] modelBytes = json.getBytes(StandardCharsets.UTF_8);

        ModelMetadata withOverrideMeta = new ModelMetadata(List.of(), ModelMetadata.Facing.AUTO, Map.of(), 1, null, false, List.of(), null);
        ModelDefinition withOverride = GltfModelParser.parse(dummyId(), modelBytes, null, withOverrideMeta);
        String overriddenName = withOverride.nodeByName("tri").mesh().primitives().get(0).material().name();
        if (!MaterialData.SKIN_MATERIAL_NAME.equals(overriddenName)) {
            return "material index 1 should be renamed to 'skin' when metadata requests it, got '" + overriddenName + "'";
        }

        ModelDefinition withoutOverride = GltfModelParser.parse(dummyId(), modelBytes, null, ModelMetadata.EMPTY);
        String plainName = withoutOverride.nodeByName("tri").mesh().primitives().get(0).material().name();
        if (!plainName.isEmpty()) {
            return "an unrelated model with no skin-material override should keep the raw (empty) name, got '" + plainName + "'";
        }
        return null;
    }

    private static String testModelInstanceAttachesPresetLayers() {
        ModelNode plain = flatNode(0, "tri", new Vector3f());
        ModelMetadata controllerMeta = new ModelMetadata(List.of(), ModelMetadata.Facing.AUTO, Map.of(), null, StandardPlayerAnimatorPreset.ID, false, List.of(), null);
        ModelDefinition withController = new ModelDefinition(dummyId(), List.of(plain), List.of(), controllerMeta);
        ModelInstance withControllerInstance = new ModelInstance(withController);
        if (withControllerInstance.animator().layers().size() != 3) {
            return "a model whose metadata names an animation controller should attach 3 preset layers (locomotion/look_procedural/face_angry), got "
                    + withControllerInstance.animator().layers().size();
        }

        ModelNode plainAgain = flatNode(0, "tri", new Vector3f());
        ModelDefinition withoutController = new ModelDefinition(dummyId(), List.of(plainAgain), List.of());
        ModelInstance withoutControllerInstance = new ModelInstance(withoutController);
        if (!withoutControllerInstance.animator().layers().isEmpty()) {
            return "a model with no animation controller in its metadata should attach no preset layers, got "
                    + withoutControllerInstance.animator().layers().size();
        }
        return null;
    }

    // ---- animation play modes (ClipPlayback.wrapTime) ----

    private static String testPlayModeOnce() {
        ClipPlayback.WrappedTime notYetEnded = ClipPlayback.wrapTime(0.5f, 1.0f, AnimationPlayMode.ONCE, false);
        if (notYetEnded.ended() || notYetEnded.time() != 0.5f) {
            return "ONCE at 0.5/1.0 should not be ended yet, got " + notYetEnded;
        }
        ClipPlayback.WrappedTime ended = ClipPlayback.wrapTime(1.5f, 1.0f, AnimationPlayMode.ONCE, false);
        if (!ended.ended() || ended.time() != 1.0f) {
            return "ONCE past duration should clamp to duration and report ended, got " + ended;
        }
        return null;
    }

    private static String testPlayModeLoop() {
        ClipPlayback.WrappedTime wrapped = ClipPlayback.wrapTime(1.5f, 1.0f, AnimationPlayMode.LOOP, false);
        if (wrapped.ended() || Math.abs(wrapped.time() - 0.5f) > 1e-5f) {
            return "LOOP at 1.5/1.0 should wrap to 0.5 and never end, got " + wrapped;
        }
        return null;
    }

    private static String testPlayModeClampForever() {
        ClipPlayback.WrappedTime clamped = ClipPlayback.wrapTime(1.5f, 1.0f, AnimationPlayMode.CLAMP_FOREVER, false);
        if (clamped.ended() || clamped.time() != 1.0f) {
            return "CLAMP_FOREVER past duration should clamp but never end, got " + clamped;
        }
        return null;
    }

    private static String testPlayModePingPong() {
        ClipPlayback.WrappedTime overshootHigh = ClipPlayback.wrapTime(1.5f, 1.0f, AnimationPlayMode.PING_PONG, false);
        if (Math.abs(overshootHigh.time() - 0.5f) > 1e-5f || !overshootHigh.reversed()) {
            return "PING_PONG overshooting past the end should reflect and flip direction, got " + overshootHigh;
        }
        ClipPlayback.WrappedTime overshootLow = ClipPlayback.wrapTime(-0.5f, 1.0f, AnimationPlayMode.PING_PONG, false);
        if (Math.abs(overshootLow.time() - 0.5f) > 1e-5f || !overshootLow.reversed()) {
            return "PING_PONG undershooting below zero should reflect and flip direction, got " + overshootLow;
        }
        return null;
    }

    // ---- bone masks ----

    private static String testBoneMaskResolvesByNameAndPath() {
        ModelNode root = flatNode(0, "Body", new Vector3f());
        ModelNode arm = flatNode(1, "RightArm", new Vector3f());
        ModelNode hand = flatNode(2, "RightHand", new Vector3f());
        root.addChild(arm);
        arm.addChild(hand);

        ModelDefinition definition = new ModelDefinition(dummyId(), List.of(root), List.of());
        ModelInstance instance = new ModelInstance(definition);
        PoseTarget target = new PoseTarget(instance.nodesByIndex(), Map.of());

        Set<Integer> byExactName = target.mask(BoneMask.of("RightHand"));
        if (byExactName.size() != 1 || !byExactName.contains(2)) {
            return "mask('RightHand') should match exactly node 2 by name, got " + byExactName;
        }

        Set<Integer> byPathSuffix = target.mask(BoneMask.of("RightArm.RightHand"));
        if (!byPathSuffix.contains(2)) {
            return "mask('RightArm.RightHand') should match node 2 via its dotted path suffix, got " + byPathSuffix;
        }
        return null;
    }

    private static String testBoneMaskExcludeWinsOverInclude() {
        ModelNode node = flatNode(0, "Head", new Vector3f());
        ModelDefinition definition = new ModelDefinition(dummyId(), List.of(node), List.of());
        ModelInstance instance = new ModelInstance(definition);
        PoseTarget target = new PoseTarget(instance.nodesByIndex(), Map.of());

        Set<Integer> resolved = target.mask(new BoneMask(Set.of("Head"), Set.of("Head")));
        if (!resolved.isEmpty()) {
            return "a name present in both includes and excludes must end up excluded, got " + resolved;
        }
        return null;
    }

    // ---- additive-against-reference-pose ----

    private static String testAdditiveReferencePose() {
        ModelNode node = flatNode(0, "Bone", new Vector3f(0f, 0f, 0f));
        ModelDefinition definition = new ModelDefinition(dummyId(), List.of(node), List.of());
        ModelInstance instance = new ModelInstance(definition);
        RuntimeNode runtime = instance.nodesByIndex().get(0);

        AnimationPose sampled = new AnimationPose();
        sampled.bone(0).translation = new Vector3f(3f, 0f, 0f);

        AnimationPose reference = new AnimationPose();
        reference.bone(0).translation = new Vector3f(1f, 0f, 0f);

        sampled.applyTo(instance.nodesByIndex(), LayerBlendMode.ADDITIVE, 1f, reference);

        // delta = sampled - reference = (2,0,0), composed additively onto the bind translation (0,0,0).
        if (Math.abs(runtime.translation().x - 2f) > 1e-5f) {
            return "additive-against-reference should apply (sampled - reference); expected x=2, got " + runtime.translation().x;
        }
        return null;
    }

    // ---- layer fade-in / fade-out / priority ----

    private static String testLayerFadeIn() {
        ClipAnimationLayerSpec spec = ClipAnimationLayerSpec.of("test", "move").withFadeIn(1.0f);
        ClipLayer layer = new ClipLayer(spec);
        AnimEvalContext context = new AnimEvalContext();

        context.deltaTime = 0f;
        float atZero = layer.weight(context);
        if (Math.abs(atZero) > 1e-5f) {
            return "weight at age 0 with fadeIn=1 should be 0, got " + atZero;
        }

        context.deltaTime = 0.5f;
        float atHalf = layer.weight(context);
        if (Math.abs(atHalf - 0.5f) > 1e-4f) {
            return "weight at age 0.5 with fadeIn=1 should be 0.5, got " + atHalf;
        }

        context.deltaTime = 1.0f; // total age now 1.5, past the fade-in window
        float atFull = layer.weight(context);
        if (Math.abs(atFull - 1f) > 1e-5f) {
            return "weight past the fade-in window should be 1, got " + atFull;
        }
        return null;
    }

    private static String testClipFadeOut() {
        ModelNode bone = new ModelNode(0, "bone", new Vector3f(), new Quaternionf(), new Vector3f(1f, 1f, 1f), null, null);
        AnimationClip clip = clipTranslating(0, new Vector3f(0f, 1f, 0f)); // named "move", duration 1
        PoseTarget target = new PoseTarget(Map.of(0, new RuntimeNode(bone, null)), Map.of("move", clip));

        ClipAnimationLayerSpec spec = ClipAnimationLayerSpec.of("test", "move")
                .withPlayMode(AnimationPlayMode.ONCE)
                .withFadeOut(1.0f);
        ClipLayer layer = new ClipLayer(spec);

        AnimEvalContext context = new AnimEvalContext();
        context.deltaTime = 1.5f; // 0.5s past the clip's own end
        LayerPose first = layer.sample(target, context);
        if (first == null) {
            return "a just-ended ONCE clip should still contribute a pose while its fade-out is in progress";
        }
        if (Math.abs(first.weightScale - 0.5f) > 1e-4f) {
            return "expected fade-out scale 0.5 halfway through a 1s fade-out window, got " + first.weightScale;
        }
        if (layer.finished()) {
            return "layer should not be finished while its fade-out is still in progress";
        }

        context.deltaTime = 1.0f; // another 1.0s past the end -> 1.5s total, past the 1.0s fade-out window
        LayerPose second = layer.sample(target, context);
        if (second != null || !layer.finished()) {
            return "layer should finish and stop contributing once its fade-out window has fully elapsed";
        }
        return null;
    }

    private static String testLayerPriorityOrder() {
        ModelNode bone = new ModelNode(0, "bone", new Vector3f(), new Quaternionf(), new Vector3f(1f, 1f, 1f), null, null);
        AnimationClip highClip = new AnimationClip("high", clipTranslating(0, new Vector3f(1f, 0f, 0f)).nodes(), 1f);
        AnimationClip lowClip = new AnimationClip("low", clipTranslating(0, new Vector3f(9f, 0f, 0f)).nodes(), 1f);
        ModelDefinition definition = new ModelDefinition(dummyId(), List.of(bone), List.of(highClip, lowClip));
        ModelInstance instance = new ModelInstance(definition);

        // Added in reverse-priority order (higher priority first in the list) — ModelAnimator must
        // still apply by priority, ascending, not by insertion order.
        instance.animator().addLayer(new ClipLayer(ClipAnimationLayerSpec.of("high", "high")
                .withPlayMode(AnimationPlayMode.LOOP).withPriority(5).withBlendMode(LayerBlendMode.OVERRIDE)));
        instance.animator().addLayer(new ClipLayer(ClipAnimationLayerSpec.of("low", "low")
                .withPlayMode(AnimationPlayMode.LOOP).withPriority(1).withBlendMode(LayerBlendMode.OVERRIDE)));
        instance.pose();

        Vector3f result = instance.nodesByIndex().get(0).translation();
        if (result.distance(new Vector3f(1f, 0f, 0f)) > 1e-4f) {
            return "the higher-priority layer should win regardless of insertion order; expected (1,0,0), got " + result;
        }
        return null;
    }

    // ---- procedural layer ----

    private static String testProceduralPoseTranslationAndScale() {
        ModelNode bone = new ModelNode(0, "bone", new Vector3f(), new Quaternionf(), new Vector3f(1f, 1f, 1f), null, null);
        ModelDefinition definition = new ModelDefinition(dummyId(), List.of(bone), List.of());
        ModelInstance instance = new ModelInstance(definition);

        ProceduralBoneTransformSpec transform = new ProceduralBoneTransformSpec("bone",
                AnimationVectorExpression.of("1", "2", "3"), null, AnimationVectorExpression.of("2", "2", "2"));
        instance.animator().addLayer(new ProceduralLayer(ProceduralLayerSpec.of("test", List.of(transform))));
        instance.pose();

        RuntimeNode node = instance.nodesByIndex().get(0);
        if (node.translation().distance(new Vector3f(1f, 2f, 3f)) > 1e-4f) {
            return "procedural translation should apply additively onto the bind translation, got " + node.translation();
        }
        if (node.scale().distance(new Vector3f(2f, 2f, 2f)) > 1e-4f) {
            return "procedural scale should apply additively (multiplicatively) onto the bind scale, got " + node.scale();
        }
        return null;
    }

    private static String testProceduralBoneRotationOrder() {
        ModelNode bone = new ModelNode(0, "bone", new Vector3f(), new Quaternionf(), new Vector3f(1f, 1f, 1f), null, null);
        ModelDefinition definition = new ModelDefinition(dummyId(), List.of(bone), List.of());
        ModelInstance instance = new ModelInstance(definition);
        PoseTarget target = new PoseTarget(instance.nodesByIndex(), Map.of());

        ProceduralBoneTransformSpec transform = new ProceduralBoneTransformSpec("bone",
                null, AnimationVectorExpression.of("90", "90", "0"), null);
        ProceduralLayer layer = new ProceduralLayer(ProceduralLayerSpec.of("test", List.of(transform)));

        LayerPose sampled = layer.sample(target, new AnimEvalContext());
        Quaternionf rotation = sampled.pose.bone(0).rotation;

        // Rz(0)*Ry(90)*Rx(90) applied to (0,0,1): Rx(90) first sends it to (0,-1,0); Ry(90) then
        // leaves a Y-axis vector alone. A swapped order (Rx applied last) would instead land on
        // (1,0,0) — distinguishable, so this pins down composition order, not just "some rotation".
        Vector3f rotated = rotation.transform(new Vector3f(0f, 0f, 1f));
        if (rotated.distance(new Vector3f(0f, -1f, 0f)) > 1e-3f) {
            return "expected Rz*Ry*Rx composition to rotate (0,0,1) to (0,-1,0), got " + rotated;
        }
        return null;
    }

    // ---- animation controller (FSM) ----

    private static String testControllerTransitionPriorityAndTieBreak() {
        List<AnimationControllerStateSpec> states = List.of(
                AnimationControllerStateSpec.of("idle", "idle"),
                AnimationControllerStateSpec.of("a", "idle"),
                AnimationControllerStateSpec.of("b", "idle"));

        // Equal priority: the lexicographically smaller target ("a") wins the tie.
        List<AnimationControllerTransitionSpec> tied = List.of(
                AnimationControllerTransitionSpec.of("idle", "b").withCondition(AnimationExpression.TRUE),
                AnimationControllerTransitionSpec.of("idle", "a").withCondition(AnimationExpression.TRUE));
        AnimationController tieController = new AnimationController(
                AnimationControllerLayerSpec.of("test", states, tied).withEntryState("idle"));
        tieController.sample(new PoseTarget(Map.of(), Map.of()), null, new AnimEvalContext());
        if (!"a".equals(tieController.stateId())) {
            return "equal-priority transitions should tie-break toward the lexicographically smaller target, got " + tieController.stateId();
        }

        // Explicit priority: "b" (priority 5) should win over "a" (priority 0) despite the alphabetical tie-break rule.
        List<AnimationControllerTransitionSpec> prioritized = List.of(
                AnimationControllerTransitionSpec.of("idle", "a").withCondition(AnimationExpression.TRUE).withPriority(0),
                AnimationControllerTransitionSpec.of("idle", "b").withCondition(AnimationExpression.TRUE).withPriority(5));
        AnimationController priorityController = new AnimationController(
                AnimationControllerLayerSpec.of("test", states, prioritized).withEntryState("idle"));
        priorityController.sample(new PoseTarget(Map.of(), Map.of()), null, new AnimEvalContext());
        if (!"b".equals(priorityController.stateId())) {
            return "the higher-priority transition should win regardless of alphabetical order, got " + priorityController.stateId();
        }
        return null;
    }

    private static String testControllerExitTime() {
        AnimationData data = new AnimationData();
        data.translation = new Vec3Track(new float[]{0f, 2f}, new Vector3f[]{new Vector3f(), new Vector3f()}, false);
        Map<Integer, AnimationData> nodes = new LinkedHashMap<>();
        nodes.put(0, data);
        AnimationClip idleClip = AnimationClip.of("idle", nodes);
        PoseTarget target = new PoseTarget(Map.of(), Map.of("idle", idleClip));

        List<AnimationControllerStateSpec> states = List.of(
                AnimationControllerStateSpec.of("idle", "idle").withPlayMode(AnimationPlayMode.LOOP),
                AnimationControllerStateSpec.of("run", "idle").withPlayMode(AnimationPlayMode.LOOP));
        AnimationControllerTransitionSpec transition = AnimationControllerTransitionSpec.of("idle", "run")
                .withCondition(AnimationExpression.TRUE)
                .withExitTime(1.0f);
        AnimationController controller = new AnimationController(
                AnimationControllerLayerSpec.of("test", states, List.of(transition)).withEntryState("idle"));

        AnimEvalContext context = new AnimEvalContext();
        context.deltaTime = 1.0f;
        controller.sample(target, null, context); // exitTime checked at stateTime=0 (not yet reached); idle then advances to 1.0
        if (!"idle".equals(controller.stateId())) {
            return "a transition with exitTime=1.0 should not fire before the current state has played that long";
        }

        controller.sample(target, null, context); // exitTime checked at stateTime=1.0 -> eligible; duration 0 commits immediately
        if (!"run".equals(controller.stateId())) {
            return "a transition with exitTime=1.0 should fire once the current state has played long enough";
        }
        return null;
    }

    private static String testControllerCrossfade() {
        AnimationClip clipA = new AnimationClip("a", clipTranslating(0, new Vector3f(2f, 0f, 0f)).nodes(), 1f);
        AnimationClip clipB = new AnimationClip("b", clipTranslating(0, new Vector3f(10f, 0f, 0f)).nodes(), 1f);
        PoseTarget target = new PoseTarget(Map.of(), Map.of("a", clipA, "b", clipB));

        List<AnimationControllerStateSpec> states = List.of(
                AnimationControllerStateSpec.of("a", "a").withPlayMode(AnimationPlayMode.LOOP),
                AnimationControllerStateSpec.of("b", "b").withPlayMode(AnimationPlayMode.LOOP));
        AnimationControllerTransitionSpec transition = AnimationControllerTransitionSpec.of("a", "b")
                .withCondition(AnimationExpression.TRUE)
                .withDuration(AnimationExpression.of("1"));
        AnimationController controller = new AnimationController(
                AnimationControllerLayerSpec.of("test", states, List.of(transition)).withEntryState("a"));

        AnimEvalContext context = new AnimEvalContext();
        context.deltaTime = 0.5f;
        AnimationPose midway = controller.sample(target, null, context);

        Vector3f mixedTranslation = midway == null ? null : midway.bone(0).translation;
        if (mixedTranslation == null || mixedTranslation.distance(new Vector3f(6f, 0f, 0f)) > 1e-3f) {
            return "mid-transition (factor 0.5 between a=2 and b=10) should give 6, got " + mixedTranslation;
        }
        if (!"a".equals(controller.stateId())) {
            return "the controller should not have committed to the target state before the crossfade finishes";
        }

        context.deltaTime = 0.6f; // elapsed now 1.1s, past the 1.0s crossfade duration
        controller.sample(target, null, context);
        if (!"b".equals(controller.stateId())) {
            return "the controller should commit to the target state once the crossfade completes";
        }
        return null;
    }

    /**
     * Regression test for a real bug in {@code AnimationController.beginTransition}: excluding
     * self-targeting transitions from the candidate pool BEFORE the priority sort (instead of after)
     * threw away the correct, highest-priority answer ("stay put") whenever a lower-priority
     * transition's condition happened to be true at the same time — which it always is for overlapping
     * conditions like {@code StandardPlayerAnimatorPreset}'s own "walk" (horizontal_speed > 0.02) and
     * "run" (is_sprinting && horizontal_speed > 0.02): "run" being true always makes "walk" true too.
     * The observable symptom was the controller perpetually crossfading run↔walk for as long as both
     * stayed true, rather than settling in "run".
     */
    private static String testControllerPersistsInSelfWinningOverLowerPriorityOverlap() {
        List<AnimationControllerStateSpec> states = List.of(
                AnimationControllerStateSpec.of("idle", "idle"),
                AnimationControllerStateSpec.of("walk", "idle"),
                AnimationControllerStateSpec.of("run", "idle"));

        List<AnimationControllerTransitionSpec> transitions = List.of(
                AnimationControllerTransitionSpec.of(AnimationControllerTransitionSpec.ANY_STATE, "walk")
                        .withCondition(AnimationExpression.TRUE).withPriority(50),
                AnimationControllerTransitionSpec.of(AnimationControllerTransitionSpec.ANY_STATE, "run")
                        .withCondition(AnimationExpression.TRUE).withPriority(70));
        AnimationController controller = new AnimationController(
                AnimationControllerLayerSpec.of("test", states, transitions).withEntryState("idle"));

        AnimEvalContext context = new AnimEvalContext();
        context.deltaTime = 1f;
        PoseTarget target = new PoseTarget(Map.of(), Map.of());

        controller.sample(target, null, context);
        if (!"run".equals(controller.stateId())) {
            return "expected the higher-priority transition (run) to win from idle, got " + controller.stateId();
        }

        controller.sample(target, null, context);
        if (!"run".equals(controller.stateId())) {
            return "the controller should stay in 'run' — its own condition is still the highest-priority true one — instead of falling back to the lower-priority 'walk', got " + controller.stateId();
        }
        return null;
    }

    // ---- posedFrame guard ----

    /**
     * Drives {@link RenderFrameClock} by hand via its test-only {@code set(long)} — {@code
     * RenderTickEvent} never fires in this headless path, so without pinning it the counter would
     * just sit at its initial value for this whole test run. A fresh {@link ModelInstance}'s own
     * {@code posedFrame} starts unset regardless of the shared counter's value, so the first
     * {@code advance()} below always really poses no matter what earlier tests left the counter at.
     */
    private static String testAdvanceSkipsDoublePoseWithinSameFrame() {
        ModelNode bone = new ModelNode(0, "bone", new Vector3f(), new Quaternionf(), new Vector3f(1f, 1f, 1f), null, null);
        ModelDefinition definition = new ModelDefinition(dummyId(), List.of(bone), List.of());
        ModelInstance instance = new ModelInstance(definition);

        RenderFrameClock.set(100L);
        instance.advance(1f);
        float afterFirst = instance.animContext().time;

        instance.advance(1f);
        if (instance.animContext().time != afterFirst) {
            return "a second advance() call within the same frame should be a no-op, but animContext().time moved from "
                    + afterFirst + " to " + instance.animContext().time;
        }

        RenderFrameClock.set(101L);
        instance.advance(1f);
        if (instance.animContext().time == afterFirst) {
            return "advance() on a new frame should really advance again, but animContext().time stayed at " + afterFirst;
        }
        return null;
    }

    // ---- PBR / LabPBR ----

    private static String testLabPbrNormalMapGreenFlip() {
        NativeImage gltfNormal = new NativeImage(1, 1, false);
        int knownRed = 200;
        int knownGreen = 60;
        gltfNormal.setPixelRGBA(0, 0, FastColor.ABGR32.color(255, 255, knownGreen, knownRed));
        NativeImage converted = LabPbrConverter.convertNormalMap(gltfNormal, null);
        try {
            int pixel = converted.getPixelRGBA(0, 0);
            int red = FastColor.ABGR32.red(pixel);
            int green = FastColor.ABGR32.green(pixel);
            if (red != knownRed) {
                return "red (tangent-X) should pass through unchanged: expected " + knownRed + ", got " + red;
            }
            int expectedGreen = 255 - knownGreen;
            if (green != expectedGreen) {
                return "green (tangent-Y) should be inverted for LabPBR's DirectX convention (glTF is OpenGL-convention): expected " + expectedGreen + ", got " + green;
            }
            return null;
        } finally {
            gltfNormal.close();
            converted.close();
        }
    }

    private static String testLabPbrNormalMapDefaults() {
        NativeImage gltfNormal = new NativeImage(1, 1, false);
        gltfNormal.setPixelRGBA(0, 0, FastColor.ABGR32.color(255, 255, 128, 128));
        NativeImage converted = LabPbrConverter.convertNormalMap(gltfNormal, null);
        try {
            int pixel = converted.getPixelRGBA(0, 0);
            int occlusion = FastColor.ABGR32.blue(pixel);
            int parallax = FastColor.ABGR32.alpha(pixel);
            if (occlusion != 255) {
                return "occlusion should default to 255 (no occlusion) when no glTF occlusion texture is present, got " + occlusion;
            }
            if (parallax != 255) {
                return "parallax (alpha) should always be 255 (flat, no parallax) — glTF has no source for it, got " + parallax;
            }
            return null;
        } finally {
            gltfNormal.close();
            converted.close();
        }
    }

    private static String testLabPbrSmoothnessAndF0Bytes() {
        if (LabPbrConverter.smoothnessByte(0f) != 255) {
            return "roughness 0 should be full smoothness (255), got " + LabPbrConverter.smoothnessByte(0f);
        }
        if (LabPbrConverter.smoothnessByte(1f) != 0) {
            return "roughness 1 should be zero smoothness (0), got " + LabPbrConverter.smoothnessByte(1f);
        }
        int expectedHalf = Math.round(255f * (1f - (float) Math.sqrt(0.25f)));
        int actualHalf = LabPbrConverter.smoothnessByte(0.25f);
        if (actualHalf != expectedHalf) {
            return "smoothnessByte(0.25) should follow LabPBR's own 1-sqrt(roughness) formula: expected " + expectedHalf + ", got " + actualHalf;
        }
        if (LabPbrConverter.f0Byte(1f) != 255) {
            return "fully metallic (1.0) should map to F0 byte 255 (use albedo as F0), got " + LabPbrConverter.f0Byte(1f);
        }
        if (LabPbrConverter.f0Byte(0f) >= 230) {
            return "fully dielectric (0.0) should map to a low F0 byte (~4% reflectance, well under the metal bands), got " + LabPbrConverter.f0Byte(0f);
        }
        if (LabPbrConverter.f0Byte(0.49f) == 255) {
            return "just below the metallic threshold (0.49) should still read as dielectric, got a metal F0 byte";
        }
        if (LabPbrConverter.f0Byte(0.5f) != 255) {
            return "the metallic threshold itself (0.5) should already read as metal (>=0.5), got " + LabPbrConverter.f0Byte(0.5f);
        }
        return null;
    }

    private static String testLabPbrF0NeverInMetalPresetBand() {
        for (int i = 0; i <= 100; i++) {
            float metallic = i / 100f;
            int f0 = LabPbrConverter.f0Byte(metallic);
            if (f0 >= 230 && f0 <= 254) {
                return "f0Byte(" + metallic + ") = " + f0 + " landed in LabPBR's predefined-real-metal band [230,254] — this "
                        + "engine never knows which real metal was intended, so it must only ever emit 255 (custom/albedo F0) or a value in [0,229]";
            }
        }
        return null;
    }

    private static String testLabPbrSpecularMapDefaults() {
        NativeImage converted = LabPbrConverter.convertSpecularMap(null, 0f, 1f, null, MaterialData.BLACK, 1, 1);
        try {
            int pixel = converted.getPixelRGBA(0, 0);
            int smoothness = FastColor.ABGR32.red(pixel);
            int f0 = FastColor.ABGR32.green(pixel);
            int porosity = FastColor.ABGR32.blue(pixel);
            int emission = FastColor.ABGR32.alpha(pixel);
            if (smoothness != 0) {
                return "roughnessFactor=1 with no metallic-roughness texture should give zero smoothness, got " + smoothness;
            }
            if (f0 >= 230) {
                return "metallicFactor=0 with no metallic-roughness texture should give a dielectric F0 byte, got " + f0;
            }
            if (porosity != 0) {
                return "porosity/SSS has no glTF source and should always default to 0, got " + porosity;
            }
            if (emission != 0) {
                return "emissiveFactor=[0,0,0] with no emissive texture should give zero emission, got " + emission;
            }
            return null;
        } finally {
            converted.close();
        }
    }
}
