package com.dimalab.storymodengine.common.model;

import net.minecraft.resources.ResourceLocation;

/**
 * Everything this engine's own PBR pipeline needs from a glTF material: base color, normal,
 * metallic-roughness, occlusion, and emissive — each an optional {@link TextureSlot} (raw encoded
 * bytes, never decoded here) plus whatever scalar factor the glTF spec pairs with it. {@code KHR_*}
 * extensions and anything not part of the core `pbrMetallicRoughness` material model are still
 * intentionally never read — see MODEL_SYSTEM_DESIGN.md's non-goals.
 *
 * <p>{@code baseColorTexCoord} is the base color texture's {@code texCoord} index (0 or 1, per
 * spec default 0) — which of a primitive's UV sets ({@code uv0}/{@code uv1}) it samples from. A
 * material that points this at {@code TEXCOORD_1} and gets ignored renders with the wrong UVs
 * silently, the same "no error, just wrong" failure the sparse-accessor fix elsewhere addressed.
 * Every other texture slot's own {@code texCoord} is carried on its {@link TextureSlot} instead,
 * since each glTF texture reference can independently pick UV0/UV1.
 *
 * <p>Every {@link TextureSlot#image()} is the texture's <b>raw encoded bytes</b> (the PNG/JPEG
 * exactly as the file carried it), deliberately not a decoded {@code NativeImage}: that type is
 * {@code @OnlyIn(Dist.CLIENT)}, and keeping it out of here is what lets {@code common.model} load
 * anywhere with no dist reasoning at all. Decoding, LabPBR conversion, and GPU upload happen
 * entirely in {@code client.model} ({@code ModelTextureLoader}, {@code pbr.LabPbrConverter}).
 *
 * <p>{@code textureKey} is the identity that base color texture registers under, derived at import
 * time from the model's own id and this material's index — {@code <ns>:model/<model path>/
 * material_<n>}. <b>Deriving it from the source, rather than from an upload counter, is what makes
 * it stable.</b> An earlier version handed out {@code model/dynamic_0}, {@code dynamic_1}, … in
 * upload order, so the same material got a different id after every reload and a texture dump said
 * nothing about which model a texture belonged to. {@link #normalKey}/{@link #specularKey}
 * derive from that same stable id — a material with no base color texture at all still needs one to
 * anchor its normal/specular map registration to, so {@code textureKeyFor} is always computed at
 * import time now, not only when a base color texture is actually present (see {@code
 * GltfMaterialImporter}).
 *
 * <p>{@code normalKey}/{@code specularKey} are themselves precomputed once at import time (via
 * {@link #normalKeyFor}/{@link #specularKeyFor}), matching {@code textureKey}'s own pattern, rather
 * than allocated fresh on every call — {@code ModelTextureLoader.normalTextureFor}/{@code
 * specularTextureFor} call these accessors once per frame per primitive, so a fresh {@code
 * ResourceLocation} there scaled with draw volume for no reason.
 *
 * <p>{@code name} is the glTF material's own {@code name} field, kept (unlike every other cosmetic
 * glTF field) because it is what a "skin" material is recognized <i>by</i> — see {@code
 * client.model.PlayerSkinSource}: a material named {@code "skin"} on an NPC with a player name/UUID
 * configured renders that player's real skin instead of this material's own baked texture. Empty,
 * never null, when the source material declared no name.
 *
 * <p>{@code alphaMode}/{@code alphaCutoff} are glTF's own alpha-handling declaration — see {@link
 * AlphaMode}'s own doc for what each value means and {@code ModelRenderTypes} for how they map to
 * render state. {@code alphaCutoff} is only meaningful under {@link AlphaMode#MASK}; it still always
 * carries a value (the spec default {@code 0.5f} when absent from the source file), never a sentinel,
 * so a caller never needs to null-check it.
 */
public record MaterialData(ResourceLocation textureKey, byte[] baseColorImage, float[] baseColorFactor,
                            int baseColorTexCoord,
                            TextureSlot normal,
                            TextureSlot metallicRoughness, float metallicFactor, float roughnessFactor,
                            TextureSlot occlusion, float occlusionStrength,
                            TextureSlot emissive, float[] emissiveFactor,
                            AlphaMode alphaMode, float alphaCutoff,
                            String name,
                            ResourceLocation normalKey, ResourceLocation specularKey) {

    public static final float[] WHITE = {1f, 1f, 1f, 1f};
    public static final float[] BLACK = {0f, 0f, 0f};

    /** The reserved material name a "skin" substitution is recognized by — see {@link #name()}. */
    public static final String SKIN_MATERIAL_NAME = "skin";

    /**
     * One optional texture reference — raw encoded bytes plus which UV set it samples ({@code
     * texCoord}, spec default 0). {@code null} (not an empty slot instance) when the source material
     * declared none, mirroring how {@code baseColorImage} being {@code null} already meant "absent"
     * before this type existed.
     */
    public record TextureSlot(byte[] image, int texCoord) {
    }

    public static MaterialData untextured() {
        return new MaterialData(null, null, WHITE, 0, null, null, 1f, 1f, null, 1f, null, BLACK, AlphaMode.OPAQUE, 0.5f, "", null, null);
    }

    public boolean hasTexture() {
        return baseColorImage != null && textureKey != null;
    }

    /**
     * The stable registration id for material {@code index} of the model at {@code modelId}. Path
     * characters a {@link ResourceLocation} rejects are folded to {@code _} so an unusual model
     * filename can't produce an illegal id.
     */
    public static ResourceLocation textureKeyFor(ResourceLocation modelId, int index) {
        String path = modelId.getPath();
        int dot = path.lastIndexOf('.');
        if (dot > 0) {
            path = path.substring(0, dot);
        }
        StringBuilder sanitized = new StringBuilder(path.length());
        for (char c : path.toLowerCase(java.util.Locale.ROOT).toCharArray()) {
            sanitized.append((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '/' || c == '.' || c == '_' || c == '-' ? c : '_');
        }
        return new ResourceLocation(modelId.getNamespace(), "model/" + sanitized + "/material_" + index);
    }

    /** {@link #textureKey} with a {@code _n} suffix, the Iris/OptiFine-standard normal-map name — {@code null} when {@code textureKey} is null. Compute once at import time and pass to the constructor; see {@link #normalKey} for the precomputed accessor. */
    public static ResourceLocation normalKeyFor(ResourceLocation textureKey) {
        return textureKey == null ? null : new ResourceLocation(textureKey.getNamespace(), textureKey.getPath() + "_n");
    }

    /** {@link #textureKey} with a {@code _s} suffix, the Iris/OptiFine-standard specular-map name — {@code null} when {@code textureKey} is null. Compute once at import time and pass to the constructor; see {@link #specularKey} for the precomputed accessor. */
    public static ResourceLocation specularKeyFor(ResourceLocation textureKey) {
        return textureKey == null ? null : new ResourceLocation(textureKey.getNamespace(), textureKey.getPath() + "_s");
    }
}
