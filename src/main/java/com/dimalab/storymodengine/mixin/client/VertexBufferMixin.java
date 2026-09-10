package com.dimalab.storymodengine.mixin.client;

import com.dimalab.storymodengine.client.model.gpu.BatchTangentBuffers;
import com.mojang.blaze3d.vertex.VertexBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The other half of the BATCHING-path {@code at_tangent} bridge (see {@code BatchTangentBuffers}' own
 * doc for the full mechanism). {@code VertexBuffer.draw()} — confirmed by direct read to be exactly
 * {@code RenderSystem.drawElements(this.mode.asGLMode, this.indexCount, this.getIndexType().asGLType)},
 * nothing else — is the actual {@code glDrawElements} call, reached only after the VAO is already
 * bound and (for the buffered path) the correct program already applied earlier in the same call
 * chain (see {@code RenderTypeMixin}'s own doc for the full chain).
 *
 * <p>{@code draw()} is also called directly by vanilla for persistent buffers (terrain, sky) with
 * nothing to do with this bridge — {@link BatchTangentBuffers#bindPendingIfMatches} and {@link
 * BatchTangentBuffers#unbindIfBound} are both true no-ops whenever nothing is staged, which is why
 * both injections are safe to apply unconditionally to every {@code draw()} call in the game rather
 * than needing to distinguish which {@code VertexBuffer} this is.
 *
 * <p>{@code HEAD} enables the attribute (only when staged data matches this exact draw's index count);
 * {@code TAIL} disables it again immediately. That pairing — never left enabled outside the narrowest
 * possible window — is what keeps this from leaking into the very next unrelated draw through the same
 * VAO (this render type shares {@code DefaultVertexFormat.NEW_ENTITY}'s one VAO with vanilla's own
 * entity rendering).
 */
@Mixin(VertexBuffer.class)
public abstract class VertexBufferMixin {

    @Shadow
    private int indexCount;

    @Inject(method = "draw()V", at = @At("HEAD"))
    private void storymodengine$bindTangent(CallbackInfo ci) {
        BatchTangentBuffers.bindPendingIfMatches(this.indexCount);
    }

    @Inject(method = "draw()V", at = @At("TAIL"))
    private void storymodengine$unbindTangent(CallbackInfo ci) {
        BatchTangentBuffers.unbindIfBound();
    }
}
