package com.dimalab.storymodengine.common.model;

/**
 * Which draw path a model's unskinned, non-morphed primitives should use — decided once per model
 * (see {@link ModelDefinition#renderPath()}), not per frame or per instance. A skinned primitive
 * always draws through {@code GpuSkinBuffers} and a morphed one always draws immediately regardless of
 * this choice (see {@code client.model.ModelRenderer#renderNode}) — this only decides between the two
 * remaining paths.
 *
 * <p>{@code PIPELINE} routes through GPU instancing (see {@code client.model.gpu.InstanceFlush} —
 * every submission draws through real instancing regardless of how many entities share it this frame,
 * an earlier per-frame instance-count fallback to an immediate draw having been removed; see that
 * class's own doc for why). {@code BATCHING} skips instancing entirely and draws immediately through
 * the ordinary {@code VertexConsumer} path — cheaper than instancing for a model built from many small
 * primitives (e.g. an unconverted Blockbench-style character with dozens of tiny cube meshes) when only
 * one or a few entities wear it, since PIPELINE's own per-primitive setup then buys little.
 *
 * <p><b>The choice is static per model, though — it can't see how many live entities will ever wear it
 * at once.</b> That's backwards for a model meant to be worn by a crowd (many NPCs sharing one
 * humanoid), where PIPELINE's instancing collapses N entities × M primitives into M draw calls total
 * regardless of N, while BATCHING's CPU-side immediate emission cost scales linearly with N — a
 * humanoid model that trips the "many small primitives" rule below can go from 30-60 fps with a
 * handful of instances to single digits with 100, entirely on the CPU, independent of GPU power. A
 * {@code .smemeta} {@code "forceRenderPath"} override (see {@code ModelMetadata#forceRenderPath}) lets
 * a script/asset author pin the path directly for exactly this case, rather than trusting the guess.
 */
public enum RenderPath {
    PIPELINE,
    BATCHING
}
