package com.dimalab.storymodengine.client.model.gpu;

import com.dimalab.storymodengine.client.model.ModelRenderTypes;
import com.dimalab.storymodengine.common.StoryModEngine;
import com.dimalab.storymodengine.common.logging.EngineLog;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL20;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * The one new shader real GPU instancing actually needs. Unlike the skinned path ({@code
 * GpuSkinBuffers}), which can draw through vanilla's own compiled entity-cutout shader because its
 * transform-feedback pre-pass already turns every vertex into ordinary model-space geometry — one
 * instance, one draw call, nothing vanilla's shader wasn't already built for — an instanced draw
 * submits <em>many</em> entities' geometry in a single {@code glDrawElementsInstanced} call, and
 * vanilla's shader has no attribute to tell those entities apart. Confirmed by HollowEngine's own
 * source: their instanced draws use a dedicated {@code gltf_entity_instanced} shader, never their
 * plain one.
 *
 * <p>{@code gltf_entity_instanced.vsh}/{@code .fsh} are otherwise a straight copy of vanilla's own
 * {@code rendertype_entity_cutout} shader pair — same uniforms, same {@code light.glsl}/{@code
 * fog.glsl} includes, same cutout/overlay/lightmap/fog math — with one addition: a
 * {@code layout(location = 7)} block of per-instance attributes ({@code InstanceModelView},
 * {@code InstanceNormalMatrix}, {@code InstanceOverlay}, {@code InstanceLight}) running 7..15,
 * placed clear of the 0-6 range {@link ShaderInstance} auto-binds for {@link
 * DefaultVertexFormat#NEW_ENTITY}'s own elements (verified against {@code ShaderInstance}'s actual
 * binding loop, not assumed) while staying inside 0..15 — the GL-guaranteed minimum for {@code
 * GL_MAX_VERTEX_ATTRIBS}. An earlier version ran 8..16, one location past that guaranteed range;
 * see {@link #verifyLinked} for what that actually did (link silently, render nothing). {@code
 * InstanceBatchBuffers} binds these manually, with {@code glVertexAttribDivisor(location, 1)},
 * since nothing about Minecraft's own vertex-format system knows what a divisor is.
 *
 * <p><b>{@link Layout}'s numbers apply only to this plain, un-patched shader.</b> The merged/pack-
 * aware program {@code OculusSMECompat} builds ({@code InstancedVertexMerger}) deliberately does
 * <em>not</em> give its own copies of these four attributes an explicit {@code layout(location=...)}
 * at all — a real bug, found and fixed during tangent-support work, came from trying to hand-pick a
 * fixed range there too. {@code net.irisshaders.iris.gl.shader.ProgramCreator.create} (the real
 * method behind Iris's own {@code createShader}, which the merged program also goes through) calls
 * {@code glBindAttribLocation} for six fixed names before every link: {@code iris_Entity}/
 * {@code mc_Entity}→11, {@code mc_midTexCoord}→12, {@code at_tangent}→13, {@code at_midBlock}→14,
 * {@code Position}→0, {@code UV0}→1. Any range this class's own instance block claims competes with
 * those six for the same 16-slot budget ({@code GL_MAX_VERTEX_ATTRIBS}'s guaranteed minimum) that a
 * pack's own dynamically-assigned attributes (its renamed {@code iris_Color}/{@code iris_UV1}/
 * {@code iris_UV2}, this engine's own un-located {@code Normal}) also need to fit into — tight enough
 * that NVIDIA's driver was observed silently aliasing two unrelated attributes to the same location
 * rather than failing the link, corrupting geometry with no error logged at all. Leaving the merged
 * copies un-located lets the linker place every attribute in that program — ours and the pack's
 * alike — with the same hard non-aliasing guarantee it already gives everything else; {@code
 * InstanceBatchBuffers} already finds them afterward by name (never by an assumed number), so this
 * costs nothing there. See {@code InstancedVertexMerger}'s own doc for the fix itself.
 */
@Mod.EventBusSubscriber(modid = StoryModEngine.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class InstancedShader {

    private static final ResourceLocation LOCATION = new ResourceLocation(StoryModEngine.MODID, "gltf_entity_instanced");

    private static ShaderInstance instance;

    /**
     * Set by an external shader-pack compat addon (e.g. a soft-dependency Oculus/Iris integration) once it has a
     * working merged {@link ShaderInstance} that carries the active pack's own lighting for this engine's per-
     * instance attribute layout — see {@link Layout}. Left {@code null} whenever no such addon is installed, or
     * whenever one is installed but has nothing usable for the current shader pack (or no pack is active), in
     * which case {@link #get()} transparently falls back to {@link #instance} as if this field didn't exist.
     */
    private static ShaderInstance override;

    private InstancedShader() {
    }

    /**
     * Called by a compat addon once it has compiled a working pack-aware replacement for {@link #instance}.
     *
     * <p>Also drops every cached {@link InstanceBatchBuffers}/instanced {@code RenderType} (via {@link
     * InstanceFlush#clearCache()}/{@link ModelRenderTypes#clearCache()}): both cache attribute <em>locations</em>
     * resolved against whichever compiled program was active when they were first built, and a program swap (a new
     * pack, or the same pack recompiling) is not guaranteed to assign the same implicit locations to {@code
     * Position}/{@code Normal}/{@code UV0}/{@code Color} (the explicit {@code layout(location = ...)} instance
     * attributes are stable across rebuilds, but those four are compiler-assigned). Left uncleared, a rebuild would
     * keep drawing through stale bindings against a program they were never actually validated against — the same
     * bug class this class's own by-name attribute lookup exists to avoid, just one level up. This was previously
     * reverted from this method specifically at the user's request (see project memory) back when nothing yet
     * exercised the instanced/pack-aware path at all; now that both the main and shadow overrides are live and
     * pack-switching is a real, exercised user action, leaving this gap open risks accumulating leaked GL objects
     * (a VAO plus five to six buffers per cached primitive, never freed) across repeated switches.
     */
    public static void setActiveOverride(ShaderInstance shader) {
        override = shader;
        InstanceFlush.clearCache();
        ModelRenderTypes.clearCache();
    }

    /** Called by a compat addon once its override stops being usable (pack disabled, pack changed, compile failed). */
    public static void clearActiveOverride() {
        override = null;
        InstanceFlush.clearCache();
        ModelRenderTypes.clearCache();
    }

    public static boolean hasActiveOverride() {
        return override != null;
    }

    /**
     * The names {@code gltf_entity_instanced.vsh} declares for its per-instance attribute block, the
     * explicit {@code layout(location = ...)} numbers <em>that same plain file</em> uses for them, and
     * the resource location of its raw vertex source — published so a compat addon that needs to
     * splice this engine's instancing logic into another shader's source (rather than just swapping
     * in a whole replacement {@link ShaderInstance} built some other way) can do so without
     * hardcoding names that only otherwise exist as a comment in the {@code .vsh} file and as
     * name-lookups in {@link InstanceBatchBuffers}.
     *
     * <p>{@code LOC_INSTANCE_*} describes only this plain shader's own hardcoded numbers (must stay
     * in sync with the literal {@code layout(location=...)} values in {@code
     * gltf_entity_instanced.vsh} — nothing reads these Java constants to generate that file). {@code
     * InstancedVertexMerger} deliberately does <em>not</em> use them when splicing this engine's
     * attributes into a pack's own donor source — see this class's own doc for why picking a fixed
     * range there is unsafe, and why an un-located declaration plus a by-name lookup afterward is not.
     */
    public static final class Layout {
        public static final ResourceLocation VERTEX_SOURCE = new ResourceLocation(StoryModEngine.MODID, "shaders/core/gltf_entity_instanced.vsh");

        public static final String ATTR_INSTANCE_MODEL_VIEW = "InstanceModelView";
        public static final String ATTR_INSTANCE_NORMAL_MATRIX = "InstanceNormalMatrix";
        public static final String ATTR_INSTANCE_OVERLAY = "InstanceOverlay";
        public static final String ATTR_INSTANCE_LIGHT = "InstanceLight";

        public static final int LOC_INSTANCE_MODEL_VIEW = 7;
        public static final int LOC_INSTANCE_NORMAL_MATRIX = 11;
        public static final int LOC_INSTANCE_OVERLAY = 14;
        public static final int LOC_INSTANCE_LIGHT = 15;

        private Layout() {
        }
    }

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) {
        try {
            ShaderInstance shader = new ShaderInstance(event.getResourceProvider(), LOCATION, DefaultVertexFormat.NEW_ENTITY);
            event.registerShader(shader, loaded -> instance = verifyLinked(loaded));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not load " + LOCATION, e);
        }
    }

    /**
     * {@code ShaderInstance}'s own constructor never throws on a link failure — {@code
     * ProgramManager.linkShader} only logs a warning and leaves the broken program installed
     * (verified in its decompiled source). Left unchecked, that produces exactly what happened once
     * already: every instanced draw runs through an unlinked program, GL logs "no active program" /
     * "not successfully linked" for each one, and nothing renders — with no crash and no obvious
     * signal pointing at the shader. Checking {@code GL_LINK_STATUS} here turns that into a clear,
     * one-line error instead.
     */
    private static ShaderInstance verifyLinked(ShaderInstance shader) {
        int linked = GL20.glGetProgrami(shader.getId(), GL20.GL_LINK_STATUS);
        if (linked == 0) {
            EngineLog.channel("Model").error("{} failed to link (program id {}) — instanced NPCs will not render. Check the log above for the GLSL linker's own message.",
                    LOCATION, shader.getId());
            return null;
        }
        return shader;
    }

    /**
     * Null until {@link RegisterShadersEvent} has fired and the shader finished compiling — not before. Returns
     * {@link #override} instead of {@link #instance} whenever one is set — see {@link #setActiveOverride}.
     */
    public static ShaderInstance get() {
        return override != null ? override : instance;
    }
}
