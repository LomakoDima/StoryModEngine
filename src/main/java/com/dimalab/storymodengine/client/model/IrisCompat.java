package com.dimalab.storymodengine.client.model;

import com.dimalab.storymodengine.client.model.gpu.InstancedShader;

import java.lang.reflect.Method;

/**
 * Soft dependency on Iris's own public compatibility API (verified directly against the class files
 * inside the Oculus 1.20.1-1.8.0 jar — Oculus is a fork of Iris and ships the same {@code
 * net.irisshaders.iris.api.v0.IrisApi} package, `javap`-disassembled to confirm the exact signatures
 * used below: {@code static IrisApi getInstance()} and {@code boolean isShaderPackInUse()}). No
 * compile-time or runtime dependency on Oculus/Iris is added anywhere — reflection only, resolved
 * once; if neither mod is installed, every call here is a cheap {@code null} check that returns
 * {@code false}.
 *
 * <p>Why this matters for rendering: {@code ModelRenderTypes.entityTriangles} (the skinned and
 * plain-unskinned draw paths) already goes through vanilla's own stock shader/vertex format, so a
 * shader pack picks those up on its own. The one path that doesn't is GPU instancing ({@code
 * InstanceFlush}/{@code InstancedShader}) — a real custom shader outside anything Iris/Oculus
 * patches. {@link #isShaderPackActive()} lets {@code ModelRenderer} route around that path only while
 * a pack is actually active, instead of giving up GPU instancing's performance unconditionally.
 */
public final class IrisCompat {

    private static final Method GET_INSTANCE;
    private static final Method IS_SHADER_PACK_IN_USE;
    private static final Method IS_RENDERING_SHADOW_PASS;

    static {
        Method getInstance = null;
        Method isShaderPackInUse = null;
        Method isRenderingShadowPass = null;
        try {
            Class<?> irisApi = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            getInstance = irisApi.getMethod("getInstance");
            isShaderPackInUse = irisApi.getMethod("isShaderPackInUse");
            isRenderingShadowPass = irisApi.getMethod("isRenderingShadowPass");
        } catch (ReflectiveOperationException e) {
            // Neither Iris nor Oculus is installed — GET_INSTANCE stays null, isShaderPackActive()
            // short-circuits to false forever, exactly as if this class didn't exist.
        }
        GET_INSTANCE = getInstance;
        IS_SHADER_PACK_IN_USE = isShaderPackInUse;
        IS_RENDERING_SHADOW_PASS = isRenderingShadowPass;
    }

    private static long cachedFrame = -1L;
    private static boolean cachedResult = false;

    private IrisCompat() {
    }

    /**
     * Cheap to call from a per-node render loop — the actual reflective call only runs once per
     * {@link RenderFrameClock} frame (a real shader-pack toggle mid-frame isn't a thing worth reacting
     * to node-by-node), cached after that the same way {@link ModelInstance}'s own {@code posedFrame}
     * guard is.
     */
    public static boolean isShaderPackActive() {
        if (GET_INSTANCE == null) {
            return false;
        }
        long frame = RenderFrameClock.currentFrame();
        if (frame != cachedFrame) {
            cachedFrame = frame;
            cachedResult = computeActive();
        }
        return cachedResult;
    }

    private static boolean computeActive() {
        try {
            Object instance = GET_INSTANCE.invoke(null);
            return (boolean) IS_SHADER_PACK_IN_USE.invoke(instance);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    /**
     * True once a shader-pack compat addon has a working, pack-aware replacement for the instanced draw path's own
     * shader installed (see {@link InstancedShader#setActiveOverride}) — lets {@link ModelRenderer} tell "a pack is
     * active but instancing still renders correctly under it" apart from "a pack is active and instancing has to
     * fall back," instead of treating every active pack as reason enough to give up instancing.
     */
    public static boolean hasReadyOverride() {
        return InstancedShader.hasActiveOverride();
    }

    /**
     * Deliberately <em>not</em> cached per {@link RenderFrameClock} frame the way {@link #isShaderPackActive()} is
     * — a shader pack's own shadow sub-pass (Iris re-invoking vanilla's own per-entity render call from the light's
     * perspective, confirmed by decompiling {@code net.irisshaders.iris.shadows.ShadowRenderer}'s {@code
     * invokeRenderEntity}) and the normal main pass both happen within the same frame, so a per-frame cache would
     * answer with whichever one happened to run first for the rest of that same frame — exactly the distinction
     * this method exists to make. Cheap enough to call uncached: one reflective call per primitive submission, not
     * per vertex.
     */
    public static boolean isRenderingShadowPass() {
        if (GET_INSTANCE == null || IS_RENDERING_SHADOW_PASS == null) {
            return false;
        }
        try {
            Object instance = GET_INSTANCE.invoke(null);
            return (boolean) IS_RENDERING_SHADOW_PASS.invoke(instance);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }
}
