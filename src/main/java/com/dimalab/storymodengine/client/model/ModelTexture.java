package com.dimalab.storymodengine.client.model;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.server.packs.resources.ResourceManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;

/**
 * A model material's GPU texture — mipmapped and driver-compressed, unlike vanilla's own {@link
 * net.minecraft.client.renderer.texture.DynamicTexture}, which this class replaces for model
 * materials (the 1x1 white fallback in {@link ModelTextureLoader#whiteTexture()} stays on {@code
 * DynamicTexture}; neither mipmaps nor compression mean anything for a single texel).
 *
 * <p>Verified against the decompiled 1.20.1-47.4.22 source before writing this: {@code
 * DynamicTexture}'s own upload path goes through {@code TextureUtil.prepareImage(id, width,
 * height)}, which hardcodes {@code NativeImage.InternalGlFormat.RGBA} with no way to ask for a
 * compressed internal format, and allocates only mip level 0 — {@code NativeImage.upload(0, 0, 0,
 * false)} (what {@code DynamicTexture.upload()} calls) always finishes by calling {@code
 * setFilter(blur=false, mipmap=false)}, i.e. every model texture today is genuinely nearest-filtered
 * with no mip chain at all — confirmed cause of the shimmer-at-distance the 2K-8K UE/Blender import
 * target described, not a guess.
 *
 * <p>The actual pixel copy is still done by vanilla's own {@link NativeImage#upload}, reused
 * unmodified — this class only replaces the one call vanilla doesn't let a caller parameterize
 * ({@code TextureUtil.prepareImage}'s hardcoded allocation), swapping in a compressed internal
 * format and following up with a hardware-generated mip chain.
 *
 * <p><b>{@code compress}</b> exists specifically for LabPBR normal/specular maps (see {@code
 * pbr.LabPbrConverter}) — generic {@code GL_COMPRESSED_RGBA} block compression is fine for a base
 * color photo texture (that's what this class was built for, and it stays {@code true} there), but a
 * well-known source of banding and distorted normals on a tangent-space normal map: each 4×4 block's
 * channels compress semi-independently, breaking the implicit unit-length relationship between a
 * normal's own X/Y/Z components. Normal and specular maps pass {@code false} and upload as plain
 * {@code GL_RGBA8} — a deliberate VRAM-for-correctness trade, not an oversight.
 */
public final class ModelTexture extends AbstractTexture {

    public ModelTexture(NativeImage pixels, boolean compress) {
        RenderSystem.assertOnRenderThreadOrInit();
        this.bind();
        // Allocates level 0 with a compressed internal format — GL_COMPRESSED_RGBA is the generic
        // form (core since GL 1.3, unlike a specific format like S3TC/DXT it needs no extension
        // check), and the driver compresses during upload. format/type here (GL_RGBA/GL_UNSIGNED_BYTE)
        // have to match what NativeImage.upload() below actually writes with — NativeImage read from
        // a PNG has Format.RGBA, whose glFormat() is exactly GL_RGBA (6408, verified against the
        // decompiled NativeImage.Format enum) — a mismatch here would upload garbage colors.
        int internalFormat = compress ? GL13.GL_COMPRESSED_RGBA : GL11.GL_RGBA8;
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, internalFormat,
                pixels.getWidth(), pixels.getHeight(), 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        // texSubImage2D's into the compressed storage just allocated; true = close pixels once done,
        // this class doesn't need the NativeImage afterward.
        pixels.upload(0, 0, 0, true);
        // Builds mip levels 1..N from level 0 in hardware, in the same compressed internal format —
        // the one piece DynamicTexture's own upload path never does at all.
        GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
        // blur=true: these are photoreal 2K-8K imports, not vanilla's own pixel-art block textures —
        // nearest-filtering a texture this dense would look aliased even close up. mipmap=true turns
        // on GL_LINEAR_MIPMAP_LINEAR minification, using the chain just generated above.
        setFilter(true, true);
    }

    @Override
    public void load(ResourceManager resourceManager) {
        // Nothing to (re)load from a resource pack path — the bytes already came from the glTF
        // material's own embedded/external image data, decoded once by ModelTextureLoader.
    }

    @Override
    public void close() {
        // AbstractTexture's own close() is a no-op by default — DynamicTexture overrides it to
        // release the GL texture id for exactly this reason (its own doc: the first version of
        // ModelTextureLoader leaked one GPU texture per material per reload before this was added).
        // Skipping this override here would reintroduce that same leak for every model texture.
        releaseId();
    }
}
