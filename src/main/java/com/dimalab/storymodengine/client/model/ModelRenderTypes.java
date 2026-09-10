package com.dimalab.storymodengine.client.model;

import com.dimalab.storymodengine.client.model.gpu.InstancedShader;
import com.dimalab.storymodengine.client.model.pbr.PbrUniformBinder;
import com.dimalab.storymodengine.common.model.AlphaMode;
import com.dimalab.storymodengine.common.model.MaterialData;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A {@link RenderType} that actually draws <b>triangles</b>.
 *
 * <p>Every vanilla entity render type is built with {@code VertexFormat.Mode.QUADS} — verified in
 * {@code RenderType}'s own source. Feeding one a triangle list therefore makes the buffer stitch each
 * quad out of vertices belonging to two different triangles: positions stay right, but faces connect
 * the wrong corners and UVs smear across them. The first version worked around that by padding every
 * triangle into a degenerate quad (third vertex repeated), which is correct but submits a third more
 * vertices than the geometry has and needed a scratch field to carry the repeated corner.
 *
 * <p>The workaround turned out to be unnecessary: {@code RenderType.create} is made <b>public</b> by
 * Forge's own access transformer, and a {@code Mode.TRIANGLES} type built on {@code
 * DefaultVertexFormat.NEW_ENTITY} can reuse the <b>stock entity-cutout shader</b> — a shader doesn't
 * care about topology, only about vertex format. So no custom {@code ShaderInstance} is involved, and
 * the earlier claim in MODEL_SYSTEM_DESIGN.md that one would be needed was simply wrong.
 *
 * <p>Extends {@code RenderType} for one reason: the state shards it composes ({@code NO_CULL},
 * {@code LIGHTMAP}, {@code OVERLAY}, the shader shard) are {@code protected static} members of
 * {@code RenderStateShard}, and a subclass is how non-vanilla code is meant to reach them. The class
 * is never instantiated.
 */
public final class ModelRenderTypes extends RenderType {

    private static final Map<ResourceLocation, RenderType> CACHE = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, RenderType> INSTANCED_CACHE = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, RenderType> TRANSLUCENT_CACHE = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, RenderType> INSTANCED_TRANSLUCENT_CACHE = new ConcurrentHashMap<>();

    /** Never called — this type exists only so the protected state shards above are in scope. */
    private ModelRenderTypes() {
        super("", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.TRIANGLES, 0, false, false, () -> {
        }, () -> {
        });
    }

    /**
     * Cutout entity rendering for {@code texture}, drawn as a triangle list. Culling stays off, as it
     * was with the previous {@code entityCutoutNoCull}: enabling it would depend on every imported
     * model having consistent winding, which is a change worth making deliberately and verifying in
     * game, not smuggling in alongside a topology fix.
     *
     * <p>Cached on {@code texture} alone within each of two separate maps — {@code material}'s
     * normal/specular maps derive deterministically from the same {@link MaterialData} the base color
     * texture came from, so a given {@code texture} never needs two different PBR bindings <em>within
     * the same alpha-mode bucket</em>. Callers must still always pass the {@link MaterialData} the
     * {@code texture} was resolved from (see {@code ModelRenderer#resolveTexture}) — the first call
     * for a given texture (per bucket) is the one that wins and gets baked into the cached
     * {@link RenderType}'s own {@code TexturingStateShard}.
     *
     * <p><b>{@link AlphaMode#BLEND} routes to a genuinely different, separately-cached {@link
     * RenderType}</b> ({@link #createTranslucent}) rather than reusing this one — an opaque and a
     * translucent material can legitimately share the same {@code texture}, and caching them together
     * would let whichever one is resolved first silently win for both. See {@link #createTranslucent}'s
     * own doc for exactly what differs. {@code OPAQUE}/{@code MASK} are unaffected by this addition —
     * both still draw through this method's own vanilla-cutout-shader path exactly as before; real
     * per-material cutoff for {@code MASK} isn't achievable here since vanilla's compiled shader can't
     * be given a new uniform (see {@code PbrUniformBinder}'s own doc for where that <em>is</em> done).
     *
     * <p><b>Tangent data on this path</b> is fed not through the shard mechanism (which runs inside
     * {@code setupRenderState()}, before the transient VAO this render type eventually draws through
     * even exists) but through a Mixin bridge: {@code ModelRenderer.emitVertex} pushes each vertex's
     * pose-transformed tangent into {@code BatchTangentCollector}, keyed by this exact {@code
     * RenderType} instance's identity; {@code RenderTypeMixin} (on {@code RenderType.end}) hands that
     * data to {@code BatchTangentBuffers} right before this type's buffer actually flushes; {@code
     * VertexBufferMixin} (on {@code VertexBuffer.draw()}) binds it as {@code at_tangent} for the
     * narrow scope of that one draw and unbinds it immediately after. That last step is load-bearing,
     * not cosmetic: this render type is built on {@code DefaultVertexFormat.NEW_ENTITY}, the same
     * format singleton vanilla itself uses for its own entity-cutout rendering, so this type's VAO is
     * literally the same object vanilla mobs draw through — an attribute left enabled here would leak
     * into the very next unrelated vanilla entity sharing it. See {@code BatchTangentBuffers}' own doc
     * for the full mechanism and the collision guard that protects against exactly that.
     *
     * <p>Reached today by a skinned-or-morphed node's non-skinned sibling call sites: a
     * {@code RenderPath.BATCHING} model's unskinned primitives, a morphed-but-unskinned primitive
     * (the instanced shader has no morph-blend stage), and the pack's own shadow pass (deliberately
     * routed here so the pack's own shadow program picks it up the way it does any vanilla entity —
     * see {@code ModelRenderer#renderNode}). {@code InstanceFlush} used to also fall back here below
     * a per-frame instance-count threshold; that fallback was removed once it became clear a lone
     * PIPELINE model — always below any such threshold — would otherwise never show normal-map
     * detail under a pack that needs {@code at_tangent}, regardless of how correct the rest of the
     * PBR pipeline was (see {@code InstanceFlush}'s own doc).
     */
    public static RenderType entityTriangles(ResourceLocation texture, MaterialData material) {
        if (material.alphaMode() == AlphaMode.BLEND) {
            return TRANSLUCENT_CACHE.computeIfAbsent(texture, t -> createTranslucent(t, material));
        }
        return CACHE.computeIfAbsent(texture, t -> create(t, material));
    }

    private static RenderType create(ResourceLocation texture, MaterialData material) {
        RenderType.CompositeState state = RenderType.CompositeState.builder()
                .setShaderState(RENDERTYPE_ENTITY_CUTOUT_NO_CULL_SHADER)
                .setTextureState(new TextureStateShard(texture, false, false))
                .setTexturingState(pbrTexturingShard(material))
                .setTransparencyState(NO_TRANSPARENCY)
                .setCullState(NO_CULL)
                .setLightmapState(LIGHTMAP)
                .setOverlayState(OVERLAY)
                .createCompositeState(true);
        return RenderType.create("storymodengine_model_triangles",
                DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.TRIANGLES, 256, true, false, state);
    }

    /**
     * The {@link AlphaMode#BLEND} sibling of {@link #create} — real alpha-over translucency instead of
     * hard cutout. Differs from {@link #create} in exactly three ways, each mirroring vanilla's own
     * non-{@code _cull}, non-emissive {@code RenderType.ENTITY_TRANSLUCENT} (confirmed by reading
     * {@code RenderType.java} directly, not assumed): a genuinely different compiled shader
     * ({@code RENDERTYPE_ENTITY_TRANSLUCENT_SHADER}, not a variant of the cutout one), {@code
     * TRANSLUCENT_TRANSPARENCY} instead of {@code NO_TRANSPARENCY}, and <b>no {@code
     * setWriteMaskState} call at all</b> — vanilla's own {@code entityTranslucent} doesn't call it
     * either, inheriting the builder's default {@code COLOR_DEPTH_WRITE} (depth-write stays on; only
     * vanilla's separate {@code _EMISSIVE} variant turns it off). Cull state stays {@code NO_CULL},
     * same reasoning {@link #create}'s own doc already gives for opaque geometry — untrusted import
     * winding, not a decision specific to blending.
     *
     * <p>No per-primitive or cross-entity depth sorting happens here, deliberately: vanilla's own
     * {@code sortOnUpload}/{@code BufferBuilder.setQuadSorting} is hard-gated to {@code Mode.QUADS}
     * (confirmed by reading {@code BufferBuilder.java} — a complete no-op for {@code Mode.TRIANGLES},
     * which this render type uses) and its sort math is itself quad-shaped, so it isn't something this
     * render type could opt into even if desired. Vanilla's own cross-entity translucency ordering is
     * no better — confirmed by reading {@code LevelRenderer.renderLevel()}: entities flush in plain
     * iteration order, backstopped only by depth-write staying on, with no global back-to-front sort
     * between separate translucent draws. This matches, rather than falls short of, that existing bar.
     */
    private static RenderType createTranslucent(ResourceLocation texture, MaterialData material) {
        RenderType.CompositeState state = RenderType.CompositeState.builder()
                .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                .setTextureState(new TextureStateShard(texture, false, false))
                .setTexturingState(pbrTexturingShard(material))
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setCullState(NO_CULL)
                .setLightmapState(LIGHTMAP)
                .setOverlayState(OVERLAY)
                .createCompositeState(true);
        return RenderType.create("storymodengine_model_triangles_translucent",
                DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.TRIANGLES, 256, true, false, state);
    }

    /**
     * Binds {@code material}'s LabPBR normal/specular maps via {@link PbrUniformBinder} — deliberately
     * as a {@code TexturingStateShard}, not folded into the texture shard above, and not called eagerly
     * wherever a primitive is emitted. See {@link PbrUniformBinder}'s own doc for the full reasoning;
     * the short version: {@code CompositeState}'s own shard list runs the texture shard <em>before</em>
     * the shader shard (confirmed by reading {@code RenderType.java}'s {@code ImmutableList.of(...)}
     * construction), so {@link RenderSystem#getShader()} is only reliably the <em>correct</em> program
     * for this draw once the shader shard has already run — {@code TexturingStateShard} runs after it
     * in that same fixed order, and (for the buffered path this render type draws through) that whole
     * sequence only executes at actual flush time ({@code RenderType.end()}), not when a primitive is
     * first queued into the buffer — exactly the moment this binding needs to happen at.
     *
     * <p>That timing is only half the story: for the buffered path this render type draws through,
     * {@code setupRenderState()} (and thus this shard) finishes running <em>before</em> the eventual
     * {@code BufferUploader.drawWithShader} call actually invokes {@code Shader#apply()} for this
     * draw — and reading a sampler uniform's texture-unit assignment is only trustworthy once
     * {@code apply()} has run at least once for this exact program (see {@link PbrUniformBinder}'s own
     * doc). {@link PbrUniformBinder#bindAfterApply} calls {@code apply()} itself before binding, so
     * this shard doesn't depend on some other entity elsewhere in the frame having already applied the
     * identical compiled program first.
     */
    private static TexturingStateShard pbrTexturingShard(MaterialData material) {
        return new TexturingStateShard("model_pbr",
                () -> PbrUniformBinder.bindAfterApply(RenderSystem.getShader(), material), () -> {
        });
    }

    /**
     * The instanced counterpart of {@link #entityTriangles} — same triangle-list, no-cull, cutout
     * shape, but through {@link InstancedShader} instead of vanilla's compiled one, since a real
     * {@code glDrawElementsInstanced} call needs the per-instance attributes only that shader
     * declares (see its own doc for why vanilla's can't be reused here the way the skinned path
     * reuses it). {@link AlphaMode#BLEND} routes to {@link #createInstancedTranslucent} instead — see
     * that method's own doc; unlike the two vanilla-shader paths, real per-material {@code MASK}
     * cutoff <em>is</em> supported here regardless of which of these two RenderTypes is picked, since
     * both reuse the same {@link InstancedShader}, whose {@code AlphaCutoff} uniform {@code
     * PbrUniformBinder} feeds per-draw (see that class's own doc).
     */
    public static RenderType instancedEntityTriangles(ResourceLocation texture, MaterialData material) {
        if (material.alphaMode() == AlphaMode.BLEND) {
            return INSTANCED_TRANSLUCENT_CACHE.computeIfAbsent(texture, ModelRenderTypes::createInstancedTranslucent);
        }
        return INSTANCED_CACHE.computeIfAbsent(texture, ModelRenderTypes::createInstanced);
    }

    private static RenderType createInstanced(ResourceLocation texture) {
        RenderType.CompositeState state = RenderType.CompositeState.builder()
                .setShaderState(new ShaderStateShard(InstancedShader::get))
                .setTextureState(new TextureStateShard(texture, false, false))
                .setTransparencyState(NO_TRANSPARENCY)
                .setCullState(NO_CULL)
                .setLightmapState(LIGHTMAP)
                .setOverlayState(OVERLAY)
                .createCompositeState(true);
        return RenderType.create("storymodengine_model_triangles_instanced",
                DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.TRIANGLES, 256, true, false, state);
    }

    /**
     * The {@link AlphaMode#BLEND} sibling of {@link #createInstanced} — same {@link InstancedShader},
     * only the transparency/write-mask GL state differs (blend behavior is controlled entirely by the
     * transparency shard, not the shader source — confirmed by reading how {@code ShaderInstance
     * .apply()}'s own blend-content caching interacts with {@code RenderStateShard}'s direct {@code
     * enableBlend}/{@code disableBlend} calls: the shard, not the shader JSON's blend block, is what
     * actually governs GL blend state at draw time). Same {@code TRANSLUCENT_TRANSPARENCY}/no-write-
     * mask-override/{@code NO_CULL} choices as {@link #createTranslucent}, same reasoning.
     */
    private static RenderType createInstancedTranslucent(ResourceLocation texture) {
        RenderType.CompositeState state = RenderType.CompositeState.builder()
                .setShaderState(new ShaderStateShard(InstancedShader::get))
                .setTextureState(new TextureStateShard(texture, false, false))
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setCullState(NO_CULL)
                .setLightmapState(LIGHTMAP)
                .setOverlayState(OVERLAY)
                .createCompositeState(true);
        return RenderType.create("storymodengine_model_triangles_instanced_translucent",
                DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.TRIANGLES, 256, true, false, state);
    }

    /** Drops cached render types; called when model textures are released so a reload doesn't keep types bound to freed textures. */
    public static void clearCache() {
        CACHE.clear();
        INSTANCED_CACHE.clear();
        TRANSLUCENT_CACHE.clear();
        INSTANCED_TRANSLUCENT_CACHE.clear();
    }
}
