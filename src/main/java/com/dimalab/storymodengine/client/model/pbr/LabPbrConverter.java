package com.dimalab.storymodengine.client.model.pbr;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.util.FastColor;

/**
 * glTF's {@code pbrMetallicRoughness} material data → LabPBR's {@code _n}/{@code _s} texture format
 * (confirmed via https://shaderlabs.org/wiki/LabPBR_Material_Standard this session — Iris's preferred
 * PBR standard, and the one this class targets; not OptiFine's older non-LabPBR format).
 *
 * <p>Pure pixel math — no GL context needed ({@link NativeImage} pixel access works without one, only
 * {@code .upload()} does), so every formula here is directly exercised by {@code
 * ModelSelfTest}'s own PBR cases without any rendering involved. See {@code PbrUniformBinder} for how
 * the textures this class produces actually reach a shader pack at draw time — nothing here binds
 * anything to GL, this class only ever produces {@link NativeImage}s.
 *
 * <h2>Known, accepted limitations — not bugs, not fixed here</h2>
 * <ul>
 *   <li><b>Normal map green-channel convention.</b> glTF's own normal-map spec is OpenGL-style
 *       (green = up); LabPBR is DirectX-style (green = down). {@link #convertNormalMap} inverts the
 *       green channel (255 - value) specifically for this — get it backwards and every normal-mapped
 *       surface looks fine in a flat-lit screenshot and only reads visibly wrong (inverted convex/
 *       concave detail) under real directional light.</li>
 *   <li><b>No parallax/height source.</b> glTF has no equivalent of LabPBR's normal-map alpha
 *       channel (height, 0=25% depth, 255=0% depth per spec) — always written as 255 (flat, no
 *       parallax), the format's own documented safe default, not a placeholder to fill in later.</li>
 *   <li><b>No porosity/subsurface-scattering source.</b> glTF has nothing analogous to LabPBR's
 *       specular-map blue channel (0-64 porosity, 65-255 SSS) — always written as 0 (no porosity),
 *       same reasoning as above.</li>
 *   <li><b>Emission is a scalar, not a color.</b> LabPBR's specular-map alpha channel is a single
 *       0-254 multiplier applied to the surface's own <em>albedo</em> color at render time — it has
 *       no room for glTF's independently-colored {@code emissiveFactor}/{@code emissiveTexture} (a
 *       red-albedo material with green glowing eyes, say). {@link #emissionByte} reduces to {@code
 *       max(r, g, b)}, discarding hue entirely. This is a real, permanent format mismatch, not
 *       something a better formula here could fix — documented, not hidden.</li>
 * </ul>
 */
public final class LabPbrConverter {

    private LabPbrConverter() {
    }

    /**
     * @param gltfNormal      a decoded glTF-convention (OpenGL green-up) tangent-space normal map.
     * @param occlusionOrNull a decoded glTF occlusion map (ambient occlusion in its R channel per
     *                        spec), or {@code null} if the material declared none — occlusion then
     *                        defaults to 255 (no occlusion), not 0, since "no data" must not read as
     *                        "fully occluded."
     * @return a new LabPBR-format {@code _n} image at {@code gltfNormal}'s own resolution: R=tangent-X
     * unchanged, G=tangent-Y inverted (see class doc), B=occlusion (nearest-sampled from {@code
     * occlusionOrNull} if present), A=255 (no parallax).
     */
    public static NativeImage convertNormalMap(NativeImage gltfNormal, NativeImage occlusionOrNull) {
        int width = gltfNormal.getWidth();
        int height = gltfNormal.getHeight();
        NativeImage out = new NativeImage(width, height, false);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int src = gltfNormal.getPixelRGBA(x, y);
                int tangentX = FastColor.ABGR32.red(src);
                int tangentY = 255 - FastColor.ABGR32.green(src);
                int occlusion = occlusionOrNull != null
                        ? FastColor.ABGR32.red(sampleNearest(occlusionOrNull, x, y, width, height))
                        : 255;
                out.setPixelRGBA(x, y, FastColor.ABGR32.color(255, occlusion, tangentY, tangentX));
            }
        }
        return out;
    }

    /**
     * @param metallicRoughnessOrNull a decoded glTF metallic-roughness map (roughness in G, metalness
     *                                in B, per spec), or {@code null} if the material declared none —
     *                                {@code metallicFactor}/{@code roughnessFactor} then apply alone.
     * @param metallicFactor          multiplies the texture's B channel (or stands alone if the
     *                                texture is absent) — spec default 1.0.
     * @param roughnessFactor         multiplies the texture's G channel (or stands alone) — spec
     *                                default 1.0.
     * @param emissiveOrNull          a decoded glTF emissive color map, or {@code null}.
     * @param emissiveFactor          RGB tint multiplied into {@code emissiveOrNull} (or stood alone
     *                                if it's absent) — spec default {@code [0,0,0]} (no emission).
     * @param fallbackWidth           output resolution when neither {@code metallicRoughnessOrNull}
     *                                nor {@code emissiveOrNull} is present (a constant-factor-only
     *                                material) — a real per-texel resolution still has to come from
     *                                somewhere even for a materially uniform output.
     * @return a new LabPBR-format {@code _s} image: R=smoothness, G=F0/metalness, B=0 (no porosity/
     * SSS), A=emission. Resolution is {@code metallicRoughnessOrNull}'s own if present, else {@code
     * emissiveOrNull}'s, else {@code fallbackWidth}×{@code fallbackHeight}.
     */
    public static NativeImage convertSpecularMap(NativeImage metallicRoughnessOrNull, float metallicFactor, float roughnessFactor,
                                                  NativeImage emissiveOrNull, float[] emissiveFactor,
                                                  int fallbackWidth, int fallbackHeight) {
        int width = metallicRoughnessOrNull != null ? metallicRoughnessOrNull.getWidth()
                : emissiveOrNull != null ? emissiveOrNull.getWidth() : fallbackWidth;
        int height = metallicRoughnessOrNull != null ? metallicRoughnessOrNull.getHeight()
                : emissiveOrNull != null ? emissiveOrNull.getHeight() : fallbackHeight;
        float er = emissiveFactor.length > 0 ? emissiveFactor[0] : 0f;
        float eg = emissiveFactor.length > 1 ? emissiveFactor[1] : 0f;
        float eb = emissiveFactor.length > 2 ? emissiveFactor[2] : 0f;

        NativeImage out = new NativeImage(width, height, false);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float roughness = roughnessFactor;
                float metallic = metallicFactor;
                if (metallicRoughnessOrNull != null) {
                    int mr = sampleNearest(metallicRoughnessOrNull, x, y, width, height);
                    roughness *= FastColor.ABGR32.green(mr) / 255f;
                    metallic *= FastColor.ABGR32.blue(mr) / 255f;
                }
                float r = er;
                float g = eg;
                float b = eb;
                if (emissiveOrNull != null) {
                    int e = sampleNearest(emissiveOrNull, x, y, width, height);
                    r *= FastColor.ABGR32.red(e) / 255f;
                    g *= FastColor.ABGR32.green(e) / 255f;
                    b *= FastColor.ABGR32.blue(e) / 255f;
                }
                int smoothness = smoothnessByte(roughness);
                int f0 = f0Byte(metallic);
                int emission = emissionByte(r, g, b);
                out.setPixelRGBA(x, y, FastColor.ABGR32.color(emission, 0, f0, smoothness));
            }
        }
        return out;
    }

    /** {@code smoothness = 1 - sqrt(roughness)}, LabPBR's own documented inverse of its perceptual-smoothness R channel. */
    public static int smoothnessByte(float roughness) {
        float smoothness = 1f - (float) Math.sqrt(clamp01(roughness));
        return Math.round(clamp01(smoothness) * 255f);
    }

    /**
     * LabPBR's G channel has three disjoint bands: 0-229 linear dielectric F0, 230-254 predefined
     * real-metal presets (meaningless here — this engine has no idea which specific real metal a
     * glTF material's metalness was meant to represent), 255 "use this surface's own albedo as its
     * F0" (LabPBR's own documented option for exactly this "some metal, unknown which" case). A
     * material is treated as fully metallic (255) at {@code metallic >= 0.5}, and as a plain
     * dielectric (~4% F0, the standard approximation glTF itself assumes for every non-metal — glTF
     * carries no separate F0/IOR field at all) otherwise. The dielectric branch is a fixed value,
     * not scaled by {@code metallic} below 0.5, since glTF gives no dielectric-F0 gradient to scale
     * from. Never emits into {@code [230, 254]} — see the class doc and {@code ModelSelfTest}'s own
     * assertion of exactly that.
     */
    public static int f0Byte(float metallic) {
        if (metallic >= 0.5f) {
            return 255;
        }
        return Math.round(0.04f * 229f / 0.9f);
    }

    /** LabPBR's A channel: 0-254 linear emission strength, reduced from an RGB color via {@code max(r,g,b)} — see the class doc's emission-color limitation. */
    public static int emissionByte(float r, float g, float b) {
        float e = Math.max(r, Math.max(g, b));
        return Math.round(clamp01(e) * 254f);
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    /** Nearest-neighbor sample of {@code source} at the position {@code (x, y)} would occupy in a {@code width}×{@code height} image — the resampling policy for source textures whose own resolution differs from the primary map being built. */
    private static int sampleNearest(NativeImage source, int x, int y, int width, int height) {
        int sx = Math.min(source.getWidth() - 1, x * source.getWidth() / width);
        int sy = Math.min(source.getHeight() - 1, y * source.getHeight() / height);
        return source.getPixelRGBA(sx, sy);
    }
}
