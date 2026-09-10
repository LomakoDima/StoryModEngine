package com.dimalab.storymodengine.client.model.pbr;

import com.dimalab.storymodengine.client.model.ModelTextureLoader;
import com.dimalab.storymodengine.common.model.MaterialData;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Binds a material's converted LabPBR normal/specular maps ({@link ModelTextureLoader#normalTextureFor}/
 * {@link ModelTextureLoader#specularTextureFor}) to a compiled GL program's {@code "normals"}/{@code
 * "specular"} uniforms — Iris's own standard names for these inputs, confirmed both by reading Iris's
 * real entity-format patching (which injects exactly these two sampler declarations into any shader
 * pack's entity fragment shader) and by reading how HollowEngine does the identical thing for the
 * identical reason (its {@code PipelineRenderer.applyMaterial}, read directly from the vendored copy
 * this session, not paraphrased).
 *
 * <p>The whole point of querying by name rather than assuming a fixed texture unit: which unit Iris
 * assigns {@code normals}/{@code specular} to is Iris's own implementation detail, and can differ
 * between packs (or between Iris versions). {@link GL20#glGetUniformi} reads back whatever unit the
 * <em>currently linked program</em> actually uses for that uniform, the same discipline {@code
 * InstanceBatchBuffers#resolveAttribute} already established for vertex attributes on this exact
 * codebase — query the compiled program, never assume, no-op cleanly when the input doesn't exist at
 * all (no pack active, or the pack's own shader never declares PBR support).
 *
 * <p><b>Reading a sampler uniform's unit is only trustworthy once something has actually
 * {@code apply()}'d the shader — a real bug, not a theoretical one.</b> Iris pushes its own per-frame
 * uniform state (which includes assigning "normals"/"specular" a real, non-clashing texture unit) as
 * part of hooking {@code Shader#apply()}, not at link time — an unapplied program's sampler uniforms
 * sit at GLSL's own link-time default of {@code 0}, silently colliding with "gtexture" (also always
 * {@code 0}), so whichever of "normals"/"specular" this class binds last ends up being what the base
 * color sampler reads too. Confirmed directly: two structurally identical Photon-compiled programs,
 * logged side by side, one whose shader had been {@code apply()}'d read back correct distinct units;
 * one that hadn't — {@code GpuSkinBuffers}' raw {@code glDrawElements} never calls {@code apply()}/
 * {@code glUseProgram} itself, "borrowing" whichever program some <em>other</em> vanilla entity's own
 * buffered draw left active this frame — read back {@code 0} for both. {@link #bindAfterApply} is the
 * one place every caller (skinned, CPU-immediate/{@code BATCHING}, and GPU-instanced paths alike)
 * should route through instead of separately judging whether {@code apply()} "probably" already ran
 * this frame — {@code apply()} is idempotent, so calling it again here costs nothing even on the
 * buffered path that will naturally call it again later at actual flush time.
 */
public final class PbrUniformBinder {

    // A linked program's own uniform locations never change, so this needs no invalidation beyond
    // what naturally happens on a shader reload/pack switch — that produces a new program id, which
    // simply gets its own fresh cache entry (the old one is left to be garbage-collected along with
    // the program itself). Mirrors BatchTangentBuffers.LOCATION_CACHE's exact idiom, already proven
    // correct in this codebase for the same reason.
    private static final Map<Integer, Map<String, Integer>> LOCATION_CACHE = new ConcurrentHashMap<>();

    private PbrUniformBinder() {
    }

    private static int uniformLocation(int programId, String uniformName) {
        return LOCATION_CACHE.computeIfAbsent(programId, id -> new ConcurrentHashMap<>())
                .computeIfAbsent(uniformName, name -> GL20.glGetUniformLocation(programId, name));
    }

    /**
     * Drops every cached uniform location; called alongside {@code BatchTangentBuffers.clearCache()}
     * on a model/shader reload. A GL driver is free to reuse a deleted program's id for an unrelated
     * new program, so a stale entry under a reused id could otherwise resolve to the wrong location
     * rather than merely a wasted cache slot.
     */
    public static void clearCache() {
        LOCATION_CACHE.clear();
    }

    /**
     * Calls {@link ShaderInstance#apply()} on {@code shader} — guaranteeing Iris's own apply()-hooked
     * per-frame uniform population (including a correct, non-clashing "normals"/"specular" texture
     * unit) has actually run for this exact program at least once — then binds through {@link #bind}.
     * A no-op if {@code shader} is {@code null} (no shader resolved yet).
     */
    public static void bindAfterApply(ShaderInstance shader, MaterialData material) {
        if (shader == null) {
            return;
        }
        shader.apply();
        bind(shader.getId(), material);
    }

    /**
     * @param programId the GL program (a stable, link-time-assigned handle — see {@code
     *                  ShaderInstance#getId()}) that will execute the draw this material belongs to.
     *                  A no-op if {@code 0} (no shader resolved yet). Prefer {@link #bindAfterApply}
     *                  unless the caller has already independently guaranteed {@code apply()} ran for
     *                  this program this frame.
     */
    public static void bind(int programId, MaterialData material) {
        if (programId == 0) {
            return;
        }
        // 3/4: fixed fallback units for this mod's own un-patched shaders (gltf_entity_instanced),
        // which Iris never touches at all (see IrisCompat's own doc) — past Sampler0/1/2 (0/1/2),
        // which this shader already uses for base color/overlay/lightmap. See bindIfDeclared's own
        // doc for why a fallback is needed at all and why it's safe alongside Iris's real assignment.
        bindIfDeclared(programId, "normals", ModelTextureLoader.normalTextureFor(material), 3);
        bindIfDeclared(programId, "specular", ModelTextureLoader.specularTextureFor(material), 4);
        bindFloatIfDeclared(programId, "AlphaCutoff", alphaCutoffFor(material));
    }

    /**
     * @param fallbackUnit the texture unit to self-assign when nothing else has claimed one — see
     *                     below. Meaningless (never reached) when the uniform isn't declared at all.
     */
    private static void bindIfDeclared(int programId, String uniformName, ResourceLocation textureKey, int fallbackUnit) {
        int location = uniformLocation(programId, uniformName);
        if (location < 0) {
            return;
        }
        int unit = GL20.glGetUniformi(programId, location);
        if (unit == 0) {
            // Nobody has assigned this uniform a real texture unit: either no shader pack is
            // active, or this is this mod's own un-patched instanced shader, which Iris never
            // touches regardless of whether a pack is active (see IrisCompat's own doc) — in either
            // case, this call is the only code that will ever set it. A genuine Iris-assigned unit
            // is never 0, since Iris itself always reserves that for the diffuse sampler, so reading
            // back 0 is a reliable "unclaimed" signal, not a false positive against a real pack.
            unit = fallbackUnit;
            GL20.glUniform1i(location, unit);
        }
        int glTextureId = Minecraft.getInstance().getTextureManager().getTexture(textureKey).getId();
        RenderSystem.activeTexture(GL13.GL_TEXTURE0 + unit);
        RenderSystem.bindTexture(glTextureId);
        // Restore unit 0 — the same discipline GpuSkinBuffers documents for its own transient
        // texture-unit use, so this doesn't leave an unexpected unit active for whatever binds next.
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);
    }

    /**
     * {@code "AlphaCutoff"} only exists in {@code gltf_entity_instanced.fsh} (this engine's own
     * compiled shader) — {@code glGetUniformLocation} returning {@code -1} for vanilla's compiled
     * cutout/translucent shaders (which declare no such uniform) makes this a clean no-op on the
     * BATCHING and skinned paths, exactly like {@link #bindIfDeclared} already is for {@code
     * "normals"}/{@code "specular"} there. Unlike those two, this sets a plain float directly —
     * no texture-unit lookup/bind needed.
     */
    private static void bindFloatIfDeclared(int programId, String uniformName, float value) {
        int location = uniformLocation(programId, uniformName);
        if (location < 0) {
            return;
        }
        GL20.glUniform1f(location, value);
    }

    /**
     * {@code MASK} uses the material's own declared cutoff; {@code BLEND} uses {@code 0.0f} — a clean
     * "never discard" sentinel, since alpha is always {@code >= 0} and the shader's test is strict
     * {@code <}; {@code OPAQUE} keeps {@code 0.1f}, this engine's own long-standing default threshold,
     * preserved here rather than switched to glTF's technically-correct "ignore alpha entirely" so
     * existing OPAQUE content doesn't change appearance.
     */
    private static float alphaCutoffFor(MaterialData material) {
        return switch (material.alphaMode()) {
            case MASK -> material.alphaCutoff();
            case BLEND -> 0.0f;
            case OPAQUE -> 0.1f;
        };
    }
}
