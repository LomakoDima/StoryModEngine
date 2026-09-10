package com.dimalab.storymodengine.mixin.client;

import com.dimalab.storymodengine.client.model.gpu.BatchTangentCollector;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

/**
 * Half of the BATCHING-path {@code at_tangent} bridge (see {@code BatchTangentBuffers}' own doc for
 * the full mechanism and why a Mixin is necessary at all). This half runs right before this {@code
 * RenderType}'s buffer actually flushes — the only point in the real call chain that knows *which*
 * {@link RenderType} is about to draw, since neither {@code BufferUploader} nor {@code VertexBuffer}
 * (where the actual draw call fires) carry a reference back to it.
 *
 * <p>Confirmed against the real decompiled source: {@code RenderType.end(BufferBuilder,
 * VertexSorting)} is exactly {@code if (buffer.building()) { ...; RenderedBuffer rendered =
 * buffer.end(); this.setupRenderState(); BufferUploader.drawWithShader(rendered);
 * this.clearRenderState(); } } — injecting right before the {@code drawWithShader} call, with local
 * capture, gets the just-built {@code RenderedBuffer} (and thus its real vertex count) without
 * duplicating {@code BufferBuilder}'s own end-of-build bookkeeping.
 *
 * <p>{@code LocalCapture.CAPTURE_FAILSOFT} rather than a hard capture: if a future Minecraft/Forge
 * update changes this method's local-variable layout in a way the capture can't match, this silently
 * stops firing (BATCHING keeps working exactly as it does without this bridge — no tangent data, same
 * as before this feature existed) instead of crashing every buffered draw in the game.
 */
@Mixin(RenderType.class)
public abstract class RenderTypeMixin {

    @Inject(
            method = "end(Lcom/mojang/blaze3d/vertex/BufferBuilder;Lcom/mojang/blaze3d/vertex/VertexSorting;)V",
            at = @At(value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/BufferUploader;drawWithShader(Lcom/mojang/blaze3d/vertex/BufferBuilder$RenderedBuffer;)V"),
            locals = LocalCapture.CAPTURE_FAILSOFT)
    private void storymodengine$stageTangents(BufferBuilder buffer, VertexSorting sorting, CallbackInfo ci,
                                               BufferBuilder.RenderedBuffer rendered) {
        BatchTangentCollector.stage((RenderType) (Object) this, rendered.drawState().vertexCount());
    }
}
