package com.dimalab.storymodengine.client.model;

import com.dimalab.storymodengine.client.model.gpu.BatchTangentCollector;
import com.dimalab.storymodengine.client.model.gpu.GpuSkinBuffers;
import com.dimalab.storymodengine.client.model.gpu.InstanceBatchCollector;
import com.dimalab.storymodengine.client.model.pbr.PbrUniformBinder;
import com.dimalab.storymodengine.common.model.MaterialData;
import com.dimalab.storymodengine.common.model.Mesh;
import com.dimalab.storymodengine.common.model.Primitive;
import com.dimalab.storymodengine.common.model.RenderPath;
import com.dimalab.storymodengine.common.model.Skin;
import com.dimalab.storymodengine.common.model.VertexData;
import com.dimalab.storymodengine.common.model.attachment.MaterialOverride;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;
import java.util.Map;

/**
 * Draws a posed {@link ModelInstance}.
 *
 * <p>Three paths, chosen per primitive:
 * <ul>
 *   <li><b>Skinned</b> — skinned on the GPU: see {@link GpuSkinBuffers}, which runs the joint-blend
 *       (and, if present, morph) in a transform-feedback pass, then draws through vanilla's own
 *       compiled entity-cutout shader. No CPU per-vertex work at all, unlike the {@link CpuSkinner}
 *       this replaced.</li>
 *   <li><b>Unskinned, no morph targets</b> — the common case for a static prop or an unrigged mesh —
 *       is <em>not</em> drawn here at all. It's handed to {@link InstanceBatchCollector}, which every
 *       entity sharing this frame feeds into, and {@code InstanceFlush} draws every entity's worth in
 *       one real {@code glDrawElementsInstanced} call after all of them have submitted. See that
 *       class's doc for why a dedicated shader (not vanilla's) is needed for this.</li>
 *   <li><b>Unskinned, morphed</b> — placed by its node's own global matrix and pushed through an
 *       ordinary {@link VertexConsumer} immediately, per entity, exactly as the unskinned path
 *       worked before instancing existed. The instanced shader has no morph-blend stage, and
 *       HollowEngine's own instancing never covers morphed geometry either, so this primitive shape
 *       stays on the immediate path rather than gaining one.</li>
 * </ul>
 *
 * <p><b>Triangles are emitted as degenerate quads</b> — see {@link #emitPrimitive}, which is where
 * the one genuinely non-obvious thing about drawing a glTF mesh in Minecraft lives. Only reached by
 * the morphed-unskinned case now; skinned and plain-unskinned primitives never touch a {@link
 * VertexConsumer}.
 */
public final class ModelRenderer {

    private final Vector3f bindPosition = new Vector3f();
    private final Vector3f bindNormal = new Vector3f();
    private final Vector3f morphedPosition = new Vector3f();
    private final Vector3f morphedNormal = new Vector3f();
    private final Vector3f transformedPosition = new Vector3f();
    private final Vector3f transformedNormal = new Vector3f();
    private final Vector3f bindTangent = new Vector3f();
    private final Vector3f transformedTangent = new Vector3f();
    private final Matrix4f nodeMatrix = new Matrix4f();
    private final Matrix4f instanceModelView = new Matrix4f();
    private final Matrix3f instanceNormalMatrix = new Matrix3f();
    private final Matrix3f nodeMatrix3 = new Matrix3f();
    private Matrix4f[] skinMatrices;
    private Skin lastComputedSkin;
    private String skinOwner = "";
    private Map<String, MaterialOverride> materialOverrides = Map.of();
    // Set once per render() call rather than re-evaluated per node in renderNode's recursive walk —
    // IrisCompat.isRenderingShadowPass() does 2 reflective Method.invoke calls, and a model's node
    // count (e.g. ~85 for player_model.gltf) turned that into ~85 reflective calls per entity per
    // frame whenever Iris/Oculus is merely installed. Safe at this granularity specifically because
    // an entity's shadow-pass invocation and its main-pass invocation are two entirely separate calls
    // to render(...), never interleaved within one call — the answer genuinely cannot change between
    // nodes of the same render() call, only between separate calls to it.
    private boolean cachedShadowPass;

    /**
     * @param skinOwner a real player's name/UUID to render a {@code "skin"}-named material as, or empty for none — see {@link PlayerSkinSource}.
     * @param materialOverrides the generic, any-material, any-entity override map (see {@code
     *                          MaterialOverrides}) — checked before {@code skinOwner}'s own narrower
     *                          {@code "skin"}-only special case, so nothing already relying on that
     *                          special case changes; empty/null when the entity has none.
     */
    public void render(ModelInstance instance, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int overlay,
                        String skinOwner, Map<String, MaterialOverride> materialOverrides) {
        this.skinOwner = skinOwner == null ? "" : skinOwner;
        this.materialOverrides = materialOverrides == null ? Map.of() : materialOverrides;
        this.lastComputedSkin = null;
        this.cachedShadowPass = IrisCompat.isRenderingShadowPass();
        PoseStack.Pose pose = poseStack.last();
        for (RuntimeNode root : instance.roots()) {
            renderNode(root, instance, pose, bufferSource, packedLight, overlay);
        }
    }

    private void renderNode(RuntimeNode node, ModelInstance instance, PoseStack.Pose pose, MultiBufferSource bufferSource, int packedLight, int overlay) {
        Mesh mesh = node.definition().mesh();
        if (mesh != null) {
            Skin skin = node.definition().skin();
            if (skin != null) {
                // A skin is shared by reference across every node that names its index (verified in
                // GltfModelParser.importSkins) — a model that splits one skinned character across
                // several mesh nodes (separate primitives for body/head/outline, say) would otherwise
                // recompute the exact same joint matrices once per node. For a rig with hundreds of
                // joints (a converted UE/Fortnite asset can easily carry 400+) that redundant work is
                // real, measured lag, not a rounding error.
                if (skin != lastComputedSkin) {
                    skinMatrices = CpuSkinner.computeSkinMatrices(skin, instance.nodesByIndex(), skinMatrices);
                    lastComputedSkin = skin;
                }
                for (Primitive primitive : mesh.primitives()) {
                    renderSkinnedPrimitive(instance, primitive, skin, node.morphWeights(), pose, packedLight, overlay);
                }
            } else {
                nodeMatrix.set(node.globalMatrix());
                // Decided once per model (see ModelDefinition#renderPath), not re-evaluated per
                // primitive: a BATCHING model always draws immediately here, same as a morphed
                // primitive already does; a PIPELINE model keeps going through the instancing
                // collector, where InstanceFlush now always draws through InstanceBatchBuffers
                // regardless of instance count (an earlier per-frame count threshold that fell back
                // to this same immediate path was removed — see InstanceFlush's own doc for why:
                // that fallback carried no tangent data, silently degrading a single PIPELINE model's
                // normal mapping under any pack that needs it).
                RenderPath renderPath = instance.definition().renderPath();
                // Two exceptions to the model's own geometry-based choice, both for the same underlying
                // reason: GPU instancing draws through a real custom shader (InstancedShader) with its
                // own VAO and glDrawElementsInstanced call, and that is the one path in this renderer a
                // shader pack's own substitution cannot see (see IrisCompat's doc). Anything a pack has
                // to do for geometry it can't see has to be rebuilt by hand, per pass, per pack; the
                // immediate path below has none of that problem, because it goes through the ordinary
                // vanilla RenderType/VertexConsumer the pack already substitutes for on its own.
                //
                // 1. A pack is active but no compat addon has installed a working pack-aware replacement
                //    for the instanced shader (IrisCompat.hasReadyOverride()) - without one, instanced
                //    geometry would render unlit by the pack.
                // 2. This is the pack's own shadow pass. Confirmed by disassembling Iris's real
                //    ShadowRenderer: it renders shadow casters by calling vanilla's own per-entity
                //    dispatch (invokeRenderEntity) with its own MultiBufferSource.BufferSource, and
                //    flushes that buffer itself (endBatch) once the pass is done. So a primitive emitted
                //    into that buffer here is picked up by the pack's own shadow program automatically,
                //    exactly like any vanilla entity - no shadow-specific shader, matrices or draw call
                //    of this engine's own. An earlier version did build and drive a merged shadow shader
                //    by hand; it was replaced by this one line, which is both simpler and correct for
                //    every pack rather than one. Instancing buys nothing here anyway - the shadow pass
                //    draws each entity once.
                if (renderPath == RenderPath.PIPELINE
                        && ((IrisCompat.isShaderPackActive() && !IrisCompat.hasReadyOverride())
                        || this.cachedShadowPass)) {
                    renderPath = RenderPath.BATCHING;
                }
                for (Primitive primitive : mesh.primitives()) {
                    if (primitive.morphTargets().isEmpty() && renderPath == RenderPath.PIPELINE) {
                        submitInstanced(primitive, pose, packedLight, overlay);
                    } else {
                        ResourceLocation texture = resolveTexture(primitive.material());
                        RenderType type = ModelRenderTypes.entityTriangles(texture, primitive.material());
                        VertexConsumer consumer = bufferSource.getBuffer(type);
                        emitPrimitive(consumer, type, primitive, node.morphWeights(), pose.pose(), pose.normal(), packedLight, overlay);
                    }
                }
            }
        }
        for (RuntimeNode child : node.children()) {
            renderNode(child, instance, pose, bufferSource, packedLight, overlay);
        }
    }

    private void renderSkinnedPrimitive(ModelInstance instance, Primitive primitive, Skin skin, float[] morphWeights, PoseStack.Pose pose, int packedLight, int overlay) {
        GpuSkinBuffers buffers = instance.gpuSkinBuffersFor(primitive, skin);
        buffers.updateAndSkin(skinMatrices, primitive.morphTargets().isEmpty() ? null : morphWeights, pose.pose(), pose.normal());

        // setupRenderState() here is synchronous (unlike the buffered CPU-immediate path — see
        // ModelRenderTypes' own doc on why its PBR binding lives in a TexturingStateShard rather than
        // being called directly), so this single call already runs that same shard, in the same fixed
        // shader-then-texturing order, before buffers.draw() below. But GpuSkinBuffers.draw() never
        // calls apply()/glUseProgram itself — by design it "borrows" whichever program some other
        // vanilla entity's own buffered draw left active this frame — so PbrUniformBinder.bindAfterApply
        // is called explicitly here rather than relying on the shard's own call (which may run before
        // any apply() has ever happened for this program this frame): see that method's own doc for why
        // reading a sampler uniform's unit isn't trustworthy until apply() has actually run.
        RenderType type = ModelRenderTypes.entityTriangles(resolveTexture(primitive.material()), primitive.material());
        type.setupRenderState();
        PbrUniformBinder.bindAfterApply(RenderSystem.getShader(), primitive.material());
        buffers.draw(type, overlay, packedLight);
        type.clearRenderState();
    }

    /**
     * Queues one primitive's placement for this frame's batched instanced draw instead of drawing it
     * now — see {@link InstanceBatchCollector}. {@code nodeMatrix} (this node's own local-to-model
     * transform, already set by the caller) is folded into the entity's pose here because the
     * instanced shader has only one per-instance model matrix; the CPU path applies the same two
     * transforms as two separate steps ({@code nodeMatrix.transformPosition} then {@code
     * poseMatrix.transformPosition}) since it has no such limit.
     */
    private void submitInstanced(Primitive primitive, PoseStack.Pose pose, int packedLight, int overlay) {
        instanceModelView.set(pose.pose()).mul(nodeMatrix);
        nodeMatrix3.set(nodeMatrix);
        instanceNormalMatrix.set(pose.normal()).mul(nodeMatrix3);
        ResourceLocation texture = resolveTexture(primitive.material());
        InstanceBatchCollector.submit(primitive, texture, instanceModelView, instanceNormalMatrix, overlay, packedLight);
    }

    /**
     * Three tiers, checked in order:
     * <ol>
     *   <li>{@link #materialOverrides} — the generic {@code entity_set_material_texture}/{@code
     *       entity_set_material_skin} mechanism, keyed by this material's own {@code name()}, works
     *       for any material name on any entity.</li>
     *   <li>The older, narrower special case: a material named {@link MaterialData#SKIN_MATERIAL_NAME}
     *       on an entity with a player name/UUID configured via {@code NpcEntity#skinOwner} — kept
     *       exactly as it always worked, so nothing already relying on it changes.</li>
     *   <li>{@link ModelTextureLoader#textureFor} — the model's own baked texture.</li>
     * </ol>
     */
    private ResourceLocation resolveTexture(MaterialData material) {
        MaterialOverride override = materialOverrides.get(material.name());
        if (override != null) {
            if (!override.texture.isEmpty()) {
                ResourceLocation parsed = ResourceLocation.tryParse(override.texture);
                if (parsed != null) {
                    return parsed;
                }
            } else if (!override.skinPlayer.isEmpty()) {
                return PlayerSkinSource.textureFor(override.skinPlayer);
            }
        }
        if (!skinOwner.isEmpty() && MaterialData.SKIN_MATERIAL_NAME.equals(material.name())) {
            return PlayerSkinSource.textureFor(skinOwner);
        }
        return ModelTextureLoader.textureFor(material);
    }

    /**
     * Writes one primitive's geometry into {@code consumer}, three vertices per triangle.
     *
     * <p>That only works because {@code consumer} comes from {@link ModelRenderTypes#entityTriangles},
     * a real {@code Mode.TRIANGLES} render type. Every <em>vanilla</em> entity render type is
     * {@code Mode.QUADS}, and feeding one a triangle list makes the buffer stitch quads across
     * triangle boundaries — the shredded geometry this rendered as before. See {@code
     * ModelRenderTypes} for why a custom triangle type needs no custom shader.
     *
     * <p>Public, and separate from {@link #renderNode}'s own texture/render-type resolution, for two
     * reasons: code integrating this into a real entity renderer may want to choose its own {@code
     * RenderType}, and {@code debug.ModelSelfTest} can drive it with a recording consumer to assert
     * vertex emission without needing a texture, a {@code Minecraft} instance, or a GL context.
     *
     * <p>Only ever reached by an <b>unskinned</b> primitive — a skinned one is drawn entirely by
     * {@link GpuSkinBuffers} instead, never through a {@link VertexConsumer}.
     *
     * <p>Takes the raw model-view/normal matrices rather than a {@link PoseStack.Pose} — the morphed-
     * primitive call site in {@link #renderNode} has a live {@code PoseStack.Pose} to pull them from
     * (via {@code pose.pose()}/{@code pose.normal()}), but the below-instancing-threshold fallback in
     * {@code InstanceFlush} only has the two matrices already captured in an {@code
     * InstanceBatchCollector.Submission} — no live {@code PoseStack} to hand it, and {@code
     * PoseStack.Pose} has no public constructor to build one from raw matrices either.
     *
     * <p>{@code renderType} is fed straight through to {@link BatchTangentCollector#submit} — it's the
     * only way that accumulator can later be matched up, by identity, with the specific flush this
     * primitive's vertices end up in (see that class's own doc). Pass {@code null} when there's no real
     * {@link RenderType}/GL context to bind against (the {@code ModelSelfTest} recording-consumer call
     * sites) — every vertex still emits normally, just without feeding the tangent bridge.
     */
    public void emitPrimitive(VertexConsumer consumer, RenderType renderType, Primitive primitive, float[] morphWeights, Matrix4f poseMatrix, Matrix3f normalMatrix, int packedLight, int overlay) {
        VertexData vertices = primitive.vertices();
        if (vertices.vertexCount() == 0) {
            return;
        }
        boolean useUv1 = primitive.material().baseColorTexCoord() == 1 && vertices.hasUv1();

        float[] factor = primitive.material().baseColorFactor();
        int r = clamp255(factor[0]);
        int g = clamp255(factor[1]);
        int b = clamp255(factor[2]);
        int a = clamp255(factor.length > 3 ? factor[3] : 1f);

        int[] indices = primitive.indices();
        List<Primitive.MorphTarget> morphTargets = primitive.morphTargets();

        for (int index : indices) {
            emitVertex(consumer, renderType, vertices, morphTargets, morphWeights, index, useUv1, poseMatrix, normalMatrix, r, g, b, a, packedLight, overlay);
        }
    }

    private void emitVertex(VertexConsumer consumer, RenderType renderType, VertexData vertices, List<Primitive.MorphTarget> morphTargets, float[] morphWeights,
                            int index, boolean useUv1,
                            Matrix4f poseMatrix, Matrix3f normalMatrix,
                            int r, int g, int b, int a, int packedLight, int overlay) {
        float[] positions = vertices.positions();
        float[] normals = vertices.normals();
        float[] uv = useUv1 ? vertices.uv1() : vertices.uv0();

        bindPosition.set(positions[index * 3], positions[index * 3 + 1], positions[index * 3 + 2]);
        if (vertices.hasNormals()) {
            bindNormal.set(normals[index * 3], normals[index * 3 + 1], normals[index * 3 + 2]);
        } else {
            bindNormal.set(0f, 1f, 0f);
        }

        applyMorph(morphTargets, morphWeights, index);

        nodeMatrix.transformPosition(morphedPosition, transformedPosition);
        nodeMatrix.transformDirection(morphedNormal, transformedNormal);

        poseMatrix.transformPosition(transformedPosition, transformedPosition);
        normalMatrix.transform(transformedNormal, transformedNormal);

        boolean hasUv = useUv1 || vertices.hasUv();
        float u = hasUv ? uv[index * 2] : 0f;
        float v = hasUv ? uv[index * 2 + 1] : 0f;

        consumer.vertex(transformedPosition.x, transformedPosition.y, transformedPosition.z)
                .color(r, g, b, a)
                .uv(u, v)
                .overlayCoords(overlay)
                .uv2(packedLight)
                .normal(transformedNormal.x, transformedNormal.y, transformedNormal.z)
                .endVertex();

        submitTangent(renderType, vertices, index, normalMatrix);
    }

    /**
     * Feeds {@link BatchTangentCollector} the same pose-transformed tangent convention vanilla's own
     * "normal" attribute already uses for entities: transformed by this node's matrix then the pose's
     * own normal matrix in Java, <em>not</em> left in raw object space — a shaderpack's {@code
     * gl_NormalMatrix * at_tangent.xyz} expects the same pre-transformed convention {@code gl_Normal}
     * already arrives in for entity rendering (see {@link #emitVertex}'s own {@code transformedNormal}
     * computation above). Called unconditionally, once per emitted vertex — even a primitive with no
     * tangent data pushes a placeholder {@code (1,0,0,1)} rather than nothing at all, so a texture
     * shared by several primitives never desyncs the accumulator's vertex count from the real one (see
     * {@link BatchTangentCollector}'s own doc). Morph targets carry no tangent deltas — the bind-pose
     * tangent is used as-is, unmorphed, matching this engine's non-goal of morphing tangent data at all.
     */
    private void submitTangent(RenderType renderType, VertexData vertices, int index, Matrix3f normalMatrix) {
        if (renderType == null) {
            return;
        }
        float tw;
        if (vertices.hasTangents()) {
            float[] tangents = vertices.tangents();
            bindTangent.set(tangents[index * 4], tangents[index * 4 + 1], tangents[index * 4 + 2]);
            tw = tangents[index * 4 + 3];
        } else {
            bindTangent.set(1f, 0f, 0f);
            tw = 1f;
        }
        nodeMatrix.transformDirection(bindTangent, transformedTangent);
        normalMatrix.transform(transformedTangent, transformedTangent);
        BatchTangentCollector.submit(renderType, transformedTangent.x, transformedTangent.y, transformedTangent.z, tw);
    }

    /**
     * Blends every active morph target's delta onto {@link #bindPosition}/{@link #bindNormal},
     * writing the result to {@link #morphedPosition}/{@link #morphedNormal}. Only reached for an
     * unskinned primitive — a skinned-and-morphed one blends in the same order (morph, then skin)
     * inside {@code gltf_skin_morph.vsh} instead.
     */
    private void applyMorph(List<Primitive.MorphTarget> morphTargets, float[] morphWeights, int index) {
        morphedPosition.set(bindPosition);
        morphedNormal.set(bindNormal);
        if (morphTargets.isEmpty() || morphWeights == null) {
            return;
        }
        for (int t = 0; t < morphTargets.size() && t < morphWeights.length; t++) {
            float weight = morphWeights[t];
            if (weight == 0f) {
                continue;
            }
            Primitive.MorphTarget target = morphTargets.get(t);
            float[] positionDeltas = target.positionDeltas();
            if (positionDeltas.length > 0) {
                morphedPosition.add(positionDeltas[index * 3] * weight, positionDeltas[index * 3 + 1] * weight, positionDeltas[index * 3 + 2] * weight);
            }
            if (target.hasNormalDeltas()) {
                float[] normalDeltas = target.normalDeltas();
                morphedNormal.add(normalDeltas[index * 3] * weight, normalDeltas[index * 3 + 1] * weight, normalDeltas[index * 3 + 2] * weight);
            }
        }
        if (morphedNormal.lengthSquared() > 1.0e-12f) {
            morphedNormal.normalize();
        }
    }

    private static int clamp255(float value) {
        return Math.max(0, Math.min(255, (int) (value * 255f)));
    }
}
