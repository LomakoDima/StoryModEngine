package com.dimalab.storymodengine.common.model.gltf;

import com.dimalab.storymodengine.api.model.ModelFormatException;
import com.dimalab.storymodengine.common.logging.EngineLog;
import com.dimalab.storymodengine.common.model.AlphaMode;
import com.dimalab.storymodengine.common.model.MaterialData;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * materials -> MaterialData: base color, normal, metallic-roughness, occlusion, and emissive, each
 * as <b>raw encoded bytes</b> plus whatever scalar factor the glTF spec pairs with it. Decoding is
 * deliberately not done here — see {@link MaterialData}'s own doc for why the decoded image belongs
 * on the client side only. {@code KHR_*} extensions and anything outside the core {@code
 * pbrMetallicRoughness} model are still intentionally never read (see MODEL_SYSTEM_DESIGN.md's
 * non-goals).
 *
 * <p><b>Every new nullable-with-a-spec-default field on {@link GltfDocument} is boxed, never a
 * primitive with a source initializer — this matters here specifically.</b> {@code GltfDocument}'s
 * own class doc explains why: Gson builds these via {@code sun.misc.Unsafe}, which allocates zeroed
 * memory and never runs {@code <init>}, so a primitive field like {@code float metallicFactor = 1f;}
 * would never actually read back {@code 1f} for a material that legally omits it — it would silently
 * import as {@code 0.0} (fully dielectric) instead of the spec's own default of fully metallic. Every
 * {@code null}-checked-then-defaulted read below ({@code metallicFactor}, {@code roughnessFactor},
 * {@code occlusionTexture.strength}, {@code emissiveFactor}) exists specifically to apply the real
 * glTF spec default in code, the way {@code baseColorFactor}'s own null-check already did.
 */
public final class GltfMaterialImporter {

    private final GltfDocument document;
    private final GltfBufferResolver buffers;
    private final ResourceLocation source;
    private final Integer skinMaterialIndex;

    public GltfMaterialImporter(GltfDocument document, GltfBufferResolver buffers, ResourceLocation source) {
        this(document, buffers, source, null);
    }

    /**
     * @param skinMaterialIndex 0-based index into {@code document.materials} that the model's
     *                          {@code .smemeta} designates as the player-skin material — see
     *                          {@code ModelMetadata#skinMaterialIndex()}. {@code null} means no override.
     */
    public GltfMaterialImporter(GltfDocument document, GltfBufferResolver buffers, ResourceLocation source, Integer skinMaterialIndex) {
        this.document = document;
        this.buffers = buffers;
        this.source = source;
        this.skinMaterialIndex = skinMaterialIndex;
    }

    public List<MaterialData> importAll() {
        List<MaterialData> out = new ArrayList<>();
        if (document.materials == null) {
            return out;
        }
        for (int i = 0; i < document.materials.size(); i++) {
            out.add(importOne(document.materials.get(i), i));
        }
        return out;
    }

    private MaterialData importOne(GltfDocument.GltfMaterial raw, int index) {
        float[] factor = MaterialData.WHITE;
        byte[] texture = null;
        int texCoord = 0;
        float metallicFactor = 1f;
        float roughnessFactor = 1f;
        MaterialData.TextureSlot metallicRoughness = null;
        if (raw.pbrMetallicRoughness != null) {
            GltfDocument.GltfPbrMetallicRoughness pbr = raw.pbrMetallicRoughness;
            if (pbr.baseColorFactor != null) {
                factor = sanitizeColorFactor(pbr.baseColorFactor);
            }
            if (pbr.baseColorTexture != null) {
                GltfDocument.GltfTextureRef ref = pbr.baseColorTexture;
                texture = imageBytesFor(ref.index);
                texCoord = ref.texCoord == null ? 0 : ref.texCoord;
            }
            metallicFactor = pbr.metallicFactor != null ? pbr.metallicFactor : 1f;
            roughnessFactor = pbr.roughnessFactor != null ? pbr.roughnessFactor : 1f;
            metallicRoughness = slotFor(pbr.metallicRoughnessTexture);
        }
        MaterialData.TextureSlot normal = slotFor(raw.normalTexture);
        MaterialData.TextureSlot occlusion = slotFor(raw.occlusionTexture);
        float occlusionStrength = raw.occlusionTexture != null && raw.occlusionTexture.strength != null
                ? raw.occlusionTexture.strength : 1f;
        MaterialData.TextureSlot emissive = slotFor(raw.emissiveTexture);
        float[] emissiveFactor = raw.emissiveFactor != null ? sanitizeColorFactor(raw.emissiveFactor) : MaterialData.BLACK;
        AlphaMode alphaMode = parseAlphaMode(raw.alphaMode);
        float alphaCutoff = raw.alphaCutoff != null ? raw.alphaCutoff : 0.5f;

        // Always computed now, not only when a base color texture is present — a material carrying
        // only a normalTexture (say) still needs a stable id to anchor its own _n/_s registration to.
        // See MaterialData's own doc on why this changed.
        ResourceLocation key = MaterialData.textureKeyFor(source, index);
        // The id is derived from the model and this material's index, so it is identical every time
        // this file is loaded — see MaterialData#textureKeyFor.
        String name = (skinMaterialIndex != null && skinMaterialIndex == index)
                ? MaterialData.SKIN_MATERIAL_NAME
                : (raw.name == null ? "" : raw.name);
        return new MaterialData(key, texture, factor, texCoord,
                normal,
                metallicRoughness, metallicFactor, roughnessFactor,
                occlusion, occlusionStrength,
                emissive, emissiveFactor,
                alphaMode, alphaCutoff,
                name,
                MaterialData.normalKeyFor(key), MaterialData.specularKeyFor(key));
    }

    /** {@code null} (absent) is the spec default {@code OPAQUE}; an unrecognized string is untrusted file input, not a format error — falls back to {@code OPAQUE} rather than throwing. */
    private AlphaMode parseAlphaMode(String raw) {
        if (raw == null) {
            return AlphaMode.OPAQUE;
        }
        try {
            return AlphaMode.valueOf(raw.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            EngineLog.channel("Model").warn("{}: unrecognized alphaMode '{}' — treating as OPAQUE", source, raw);
            return AlphaMode.OPAQUE;
        }
    }

    /**
     * {@code baseColorFactor}/{@code emissiveFactor} flow straight into {@code GpuSkinBuffers
     * .createConstantColorBuffer} as literal per-vertex color-attribute bytes — the same "raw file
     * field, no validation, straight to the GPU" shape the original tangent bug had, just for color
     * instead of geometry. Clamps each component into {@code [0,1]} and substitutes a finite default
     * for any non-finite one, rather than trusting whatever a file happens to declare.
     */
    private static float[] sanitizeColorFactor(float[] factor) {
        float[] sanitized = new float[factor.length];
        for (int i = 0; i < factor.length; i++) {
            float value = factor[i];
            sanitized[i] = Float.isFinite(value) ? Math.max(0f, Math.min(1f, value)) : (i == 3 ? 1f : 0f);
        }
        return sanitized;
    }

    private MaterialData.TextureSlot slotFor(GltfDocument.GltfTextureRef ref) {
        if (ref == null) {
            return null;
        }
        byte[] bytes = imageBytesFor(ref.index);
        if (bytes == null) {
            return null;
        }
        return new MaterialData.TextureSlot(bytes, ref.texCoord == null ? 0 : ref.texCoord);
    }

    private byte[] imageBytesFor(int textureIndex) {
        if (document.textures == null || textureIndex >= document.textures.size()) {
            EngineLog.channel("Model").warn("{}: texture index {} out of range — material will render untextured", source, textureIndex);
            return null;
        }
        GltfDocument.GltfTexture texture = document.textures.get(textureIndex);
        if (texture.source == null || document.images == null) {
            return null;
        }
        try {
            return imageBytes(document.images.get(texture.source));
        } catch (Exception e) {
            EngineLog.channel("Model").error("{}: failed to read image for texture {} — material will render untextured", source, textureIndex, e);
            return null;
        }
    }

    private byte[] imageBytes(GltfDocument.GltfImage image) {
        if (image.uri != null) {
            if (image.uri.startsWith("data:")) {
                int comma = image.uri.indexOf(',');
                return Base64.getDecoder().decode(image.uri.substring(comma + 1));
            }
            return buffers.readSibling(image.uri);
        }
        if (image.bufferView != null) {
            GltfDocument.GltfBufferView view = document.bufferViews.get(image.bufferView);
            byte[] bufferBytes = buffers.resolve(view.buffer);
            int offset = view.byteOffset == null ? 0 : view.byteOffset;
            byte[] slice = new byte[view.byteLength];
            System.arraycopy(bufferBytes, offset, slice, 0, view.byteLength);
            return slice;
        }
        throw new ModelFormatException(source, "image has neither uri nor bufferView");
    }
}
