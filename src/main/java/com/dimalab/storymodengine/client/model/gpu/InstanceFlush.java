package com.dimalab.storymodengine.client.model.gpu;

import com.dimalab.storymodengine.client.model.ModelRenderTypes;
import com.dimalab.storymodengine.client.model.pbr.PbrUniformBinder;
import com.dimalab.storymodengine.common.StoryModEngine;
import com.dimalab.storymodengine.common.model.Primitive;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Flushes every {@link InstanceBatchCollector} submission made this frame into one {@code
 * glDrawElementsInstanced} call per {@link Primitive} group, at {@code
 * RenderLevelStageEvent.Stage.AFTER_ENTITIES} — after every entity's own {@code ModelRenderer.render}
 * has already run (and queued into the collector instead of drawing immediately) for this frame, so
 * every instance sharing a primitive this frame is known by the time this runs.
 *
 * <p>The one correctness fix this path needed beyond the skinned path: {@code
 * RenderSystem.setShader(supplier)} only stores a static field — it does <em>not</em> upload
 * uniforms. Only {@code VertexBuffer._drawWithShader(...)} does that, by explicitly populating each
 * of the shader's own {@code Uniform} fields (verified directly in {@code VertexBuffer.java}) from
 * current {@code RenderSystem} state, then calling {@code apply()}. The skinned path never needed
 * this because it draws through vanilla's <em>own</em> compiled entity-cutout shader — some other
 * ordinary entity draw earlier in the very same frame already ran that shader through {@code
 * _drawWithShader} and left its uniforms populated. {@link InstancedShader}'s shader has no such
 * other consumer, so {@link #applyGlobalUniforms} reproduces {@code _drawWithShader}'s own uniform
 * population by hand before every instanced draw.
 *
 * <p><b>Every submission draws through {@link InstanceBatchBuffers}, regardless of count</b> — an
 * earlier version fell back to the CPU-immediate {@code BufferBuilder} path (see {@code
 * ModelRenderer#emitPrimitive}) below a per-primitive instance-count threshold ({@code
 * InstancingThreshold}, since removed), reasoning that packing and uploading a whole instance buffer
 * for very few instances cost more than it saved. That path never carried tangent data or PBR
 * uniform binding (its {@code TexturingStateShard} hook exists, but {@code at_tangent} has nowhere
 * safe to bind on a transient, vanilla-owned VAO — see {@code ModelRenderTypes}' own doc), so a lone
 * test model — always below any instance-count threshold — silently never showed normal-map detail
 * regardless of how correct the rest of the PBR pipeline was. {@code glDrawElementsInstanced} with a
 * count of 1 is a fully ordinary, correct GL call — HollowEngine's own reference implementation
 * (confirmed by reading {@code PipelineRenderer.kt} directly) never falls back to a
 * non-instanced/BufferBuilder draw for this shape of geometry at all — so the performance argument
 * for the threshold, real as it was, is not worth the correctness gap it reopened.
 */
@Mod.EventBusSubscriber(modid = StoryModEngine.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class InstanceFlush {

    private static final Map<Primitive, InstanceBatchBuffers> BUFFERS = new IdentityHashMap<>();

    /** Precomputed once instead of concatenating {@code "Sampler" + i} on every flushed primitive, every frame. */
    private static final String[] SAMPLER_NAMES = {
            "Sampler0", "Sampler1", "Sampler2", "Sampler3", "Sampler4", "Sampler5",
            "Sampler6", "Sampler7", "Sampler8", "Sampler9", "Sampler10", "Sampler11"
    };

    private InstanceFlush() {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        Map<Primitive, InstanceBatchCollector.Batch> batches = InstanceBatchCollector.drainAndClear();
        if (batches.isEmpty()) {
            return;
        }
        ShaderInstance shader = InstancedShader.get();
        if (shader == null) {
            return;
        }

        for (Map.Entry<Primitive, InstanceBatchCollector.Batch> entry : batches.entrySet()) {
            Primitive primitive = entry.getKey();
            InstanceBatchCollector.InstanceData instanceData = entry.getValue().data();
            ResourceLocation texture = entry.getValue().texture();

            InstanceBatchBuffers buffers = BUFFERS.computeIfAbsent(primitive, InstanceBatchBuffers::new);

            // setupRenderState() runs this RenderType's ShaderStateShard, which itself calls
            // RenderSystem.setShader(InstancedShader::get) — confirmed in RenderStateShard.java, so
            // no separate setShader call is needed here — plus its texture/overlay/lightmap shards,
            // which populate RenderSystem's shader-texture units 0/1/2 that applyGlobalUniforms below
            // reads back out via RenderSystem.getShaderTexture(i).
            RenderType type = ModelRenderTypes.instancedEntityTriangles(texture, primitive.material());
            type.setupRenderState();
            applyGlobalUniforms(shader);
            // Confirmed, not assumed: OculusInstancingIntegration.rebuild() passes the active
            // pack's own fragment source into sme$createShader completely unmodified (see
            // InstancedVertexMerger's own class doc — only the vertex stage is ever merged), so
            // whatever "normals"/"specular" uniforms the pack's real gbuffers_entities.fsh
            // declares survive into this merged program exactly as authored. shader.getId() is
            // already held directly here — no ambient-state lookup needed, unlike the CPU/skinned
            // paths (see ModelRenderTypes' own doc for why those go through a TexturingStateShard
            // instead).
            PbrUniformBinder.bindAfterApply(shader, primitive.material());
            buffers.draw(instanceData);
            shader.clear();
            type.clearRenderState();
        }
    }

    /**
     * Reproduces {@code VertexBuffer._drawWithShader}'s uniform <em>population</em> by hand — see
     * class doc for why this shader has no other draw to piggyback on. Deliberately does not call
     * {@code shader.apply()} itself — the caller does that via {@link PbrUniformBinder#bindAfterApply},
     * the one place every render path routes an {@code apply()} call through (see that method's doc).
     */
    private static void applyGlobalUniforms(ShaderInstance shader) {
        for (int i = 0; i < 12; i++) {
            shader.setSampler(SAMPLER_NAMES[i], RenderSystem.getShaderTexture(i));
        }
        if (shader.MODEL_VIEW_MATRIX != null) {
            shader.MODEL_VIEW_MATRIX.set(RenderSystem.getModelViewMatrix());
        }
        if (shader.PROJECTION_MATRIX != null) {
            shader.PROJECTION_MATRIX.set(RenderSystem.getProjectionMatrix());
        }
        if (shader.COLOR_MODULATOR != null) {
            shader.COLOR_MODULATOR.set(RenderSystem.getShaderColor());
        }
        if (shader.FOG_START != null) {
            shader.FOG_START.set(RenderSystem.getShaderFogStart());
        }
        if (shader.FOG_END != null) {
            shader.FOG_END.set(RenderSystem.getShaderFogEnd());
        }
        if (shader.FOG_COLOR != null) {
            shader.FOG_COLOR.set(RenderSystem.getShaderFogColor());
        }
        if (shader.FOG_SHAPE != null) {
            shader.FOG_SHAPE.set(RenderSystem.getShaderFogShape().getIndex());
        }
        RenderSystem.setupShaderLights(shader);
    }

    /** Frees every cached {@link InstanceBatchBuffers} — called when model textures are released so a reload doesn't keep GL buffers bound to freed textures, mirroring {@code ModelRenderTypes.clearCache()}. */
    public static void clearCache() {
        for (InstanceBatchBuffers buffers : BUFFERS.values()) {
            buffers.destroy();
        }
        BUFFERS.clear();
    }
}
