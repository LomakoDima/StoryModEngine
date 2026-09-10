package com.dimalab.storymodengine.client.model;

import com.dimalab.storymodengine.client.model.pbr.LabPbrConverter;
import com.dimalab.storymodengine.common.logging.EngineLog;
import com.dimalab.storymodengine.common.model.AlphaMode;
import com.dimalab.storymodengine.common.model.MaterialData;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Decodes a {@link MaterialData}'s raw image bytes and uploads them as {@link ModelTexture}s
 * (mipmapped; base color driver-compressed, normal/specular not — see that class's own doc), lazily
 * and once per material. This is the only class in the model system that touches a client-only image
 * type at all, which is what keeps {@code common.model} free of {@code @OnlyIn} concerns entirely.
 * The 1x1 white/flat-normal/default-specular fallbacks stay on vanilla's own {@link DynamicTexture}
 * — neither mipmaps nor compression mean anything for one texel.
 *
 * <p><b>Registered textures are released on reload</b> via {@link #releaseAll()}. The first version
 * of this class never did, so every {@code /reload} leaked one GPU texture per material for the
 * lifetime of the game — invisible until enough reloads had happened to matter. {@link
 * #normalTextureFor}/{@link #specularTextureFor} join the same {@link #REGISTERED} set as {@link
 * #textureFor}, so this still holds for them with no changes to {@link #releaseAll()} itself.
 */
public final class ModelTextureLoader {

    private static final Set<ResourceLocation> REGISTERED = new LinkedHashSet<>();
    private static ResourceLocation whiteTexture;
    private static ResourceLocation flatNormalTexture;
    private static ResourceLocation defaultSpecularTexture;

    private ModelTextureLoader() {
    }

    /**
     * The texture to bind for {@code material} — its own base color texture, or a shared 1×1 white
     * fallback when it has none (or its image failed to decode). The fallback keeps an untextured
     * material rendering as a flat {@code baseColorFactor}-tinted mesh rather than disappearing.
     */
    public static ResourceLocation textureFor(MaterialData material) {
        if (!material.hasTexture()) {
            return whiteTexture();
        }
        ResourceLocation key = material.textureKey();
        // The key is derived from the model, so "already uploaded" is a set membership test rather
        // than a lookup keyed on the material object — and the id stays the same across reloads.
        if (REGISTERED.contains(key)) {
            return key;
        }
        return upload(key, material);
    }

    /**
     * The LabPBR normal map ({@link MaterialData#normalKeyFor}) converted from {@code
     * material.normal()} (plus {@code material.occlusion()} for the map's own AO channel — see {@code
     * LabPbrConverter#convertNormalMap}), or a shared 1×1 flat-normal fallback when the material
     * declared no normal texture at all (or it failed to decode). {@link PbrUniformBinder} binds this
     * unconditionally regardless of whether a normal map was actually authored — the flat fallback is
     * what makes that safe: a pack sampling it reads "no normal detail," not garbage.
     */
    public static ResourceLocation normalTextureFor(MaterialData material) {
        if (material.normal() == null) {
            return flatNormalTexture();
        }
        ResourceLocation key = material.normalKey();
        if (REGISTERED.contains(key)) {
            return key;
        }
        return uploadNormal(key, material);
    }

    /**
     * The LabPBR specular map ({@link MaterialData#specularKeyFor}) converted from {@code
     * material.metallicRoughness()}/{@code material.emissive()} (plus their factors — see {@code
     * LabPbrConverter#convertSpecularMap}), or a shared 1×1 default-specular fallback (zero
     * smoothness, ~4% dielectric F0, no emission) when the material declared neither map and both
     * factors are at their spec defaults.
     */
    public static ResourceLocation specularTextureFor(MaterialData material) {
        boolean hasAnySource = material.metallicRoughness() != null || material.emissive() != null
                || material.metallicFactor() != 1f || material.roughnessFactor() != 1f
                || material.emissiveFactor()[0] != 0f || material.emissiveFactor()[1] != 0f || material.emissiveFactor()[2] != 0f;
        if (!hasAnySource) {
            return defaultSpecularTexture();
        }
        ResourceLocation key = material.specularKey();
        if (REGISTERED.contains(key)) {
            return key;
        }
        return uploadSpecular(key, material);
    }

    private static ResourceLocation upload(ResourceLocation key, MaterialData material) {
        try (NativeImage image = NativeImage.read(new ByteArrayInputStream(material.baseColorImage()))) {
            // MASK's own cutoff is baked into the pixels themselves here — every remaining alpha is
            // already exactly 0 or 255 — rather than relying on a per-shader uniform, since two of
            // this engine's three render paths reuse vanilla's own compiled shaders and can't be
            // given a new one (see PbrUniformBinder.bindFloatIfDeclared's own doc). A renderer's own
            // alpha test, whatever fixed or authored threshold it uses, produces the identical result
            // against a value that's already binary.
            if (material.alphaMode() == AlphaMode.MASK) {
                binarizeAlpha(image, material.alphaCutoff());
            }
            // Unlike the old DynamicTexture path, ModelTexture closes the NativeImage it's given
            // itself (via pixels.upload(..., true)) — no need to copy through a fresh owned instance
            // first, since nothing outside this try-with-resources keeps the original around.
            Minecraft.getInstance().getTextureManager().register(key, new ModelTexture(image, true));
            REGISTERED.add(key);
            return key;
        } catch (IOException e) {
            EngineLog.channel("Model").error("Failed to decode base color texture " + key + " — material will render untextured", e);
            return whiteTexture();
        }
    }

    /** Rewrites every pixel's alpha to a hard 0 or 255 against {@code cutoff} — see {@link #upload}'s own doc for why. */
    private static void binarizeAlpha(NativeImage image, float cutoff) {
        int threshold = Math.round(cutoff * 255f);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getPixelRGBA(x, y);
                int a = FastColor.ABGR32.alpha(argb) >= threshold ? 255 : 0;
                image.setPixelRGBA(x, y, FastColor.ABGR32.color(a,
                        FastColor.ABGR32.blue(argb), FastColor.ABGR32.green(argb), FastColor.ABGR32.red(argb)));
            }
        }
    }

    private static ResourceLocation uploadNormal(ResourceLocation key, MaterialData material) {
        try (NativeImage gltfNormal = NativeImage.read(new ByteArrayInputStream(material.normal().image()));
             NativeImage occlusion = material.occlusion() != null ? NativeImage.read(new ByteArrayInputStream(material.occlusion().image())) : null) {
            NativeImage converted = LabPbrConverter.convertNormalMap(gltfNormal, occlusion);
            Minecraft.getInstance().getTextureManager().register(key, new ModelTexture(converted, false));
            REGISTERED.add(key);
            return key;
        } catch (IOException e) {
            EngineLog.channel("Model").error("Failed to decode normal map " + key + " — material will render with no normal detail", e);
            return flatNormalTexture();
        }
    }

    private static ResourceLocation uploadSpecular(ResourceLocation key, MaterialData material) {
        NativeImage metallicRoughness = null;
        NativeImage emissive = null;
        try {
            if (material.metallicRoughness() != null) {
                metallicRoughness = NativeImage.read(new ByteArrayInputStream(material.metallicRoughness().image()));
            }
            if (material.emissive() != null) {
                emissive = NativeImage.read(new ByteArrayInputStream(material.emissive().image()));
            }
            NativeImage converted = LabPbrConverter.convertSpecularMap(metallicRoughness, material.metallicFactor(), material.roughnessFactor(),
                    emissive, material.emissiveFactor(), 1, 1);
            Minecraft.getInstance().getTextureManager().register(key, new ModelTexture(converted, false));
            REGISTERED.add(key);
            return key;
        } catch (IOException e) {
            EngineLog.channel("Model").error("Failed to decode metallic-roughness/emissive map " + key + " — material will render with no PBR response", e);
            return defaultSpecularTexture();
        } finally {
            if (metallicRoughness != null) {
                metallicRoughness.close();
            }
            if (emissive != null) {
                emissive.close();
            }
        }
    }

    private static synchronized ResourceLocation whiteTexture() {
        if (whiteTexture == null) {
            NativeImage image = new NativeImage(1, 1, false);
            image.setPixelRGBA(0, 0, 0xFFFFFFFF);
            DynamicTexture texture = new DynamicTexture(image);
            whiteTexture = new ResourceLocation("storymodengine", "model/white");
            Minecraft.getInstance().getTextureManager().register(whiteTexture, texture);
        }
        return whiteTexture;
    }

    /** LabPBR's own documented neutral tangent-space normal — R=128 (X centered), G=128 (Y centered, pre-flip so this is also correct post-flip), B=255 (no occlusion), A=255 (no parallax). */
    private static synchronized ResourceLocation flatNormalTexture() {
        if (flatNormalTexture == null) {
            NativeImage image = new NativeImage(1, 1, false);
            image.setPixelRGBA(0, 0, FastColor.ABGR32.color(255, 255, 128, 128));
            DynamicTexture texture = new DynamicTexture(image);
            flatNormalTexture = new ResourceLocation("storymodengine", "model/flat_normal");
            Minecraft.getInstance().getTextureManager().register(flatNormalTexture, texture);
        }
        return flatNormalTexture;
    }

    /** Zero smoothness (roughness=1), ~4% dielectric F0, no porosity/SSS, no emission — the same {@link LabPbrConverter} formulas the real conversion path uses, at roughness=1/metallic=0/no emission. */
    private static synchronized ResourceLocation defaultSpecularTexture() {
        if (defaultSpecularTexture == null) {
            NativeImage image = new NativeImage(1, 1, false);
            int smoothness = LabPbrConverter.smoothnessByte(1f);
            int f0 = LabPbrConverter.f0Byte(0f);
            image.setPixelRGBA(0, 0, FastColor.ABGR32.color(0, 0, f0, smoothness));
            DynamicTexture texture = new DynamicTexture(image);
            defaultSpecularTexture = new ResourceLocation("storymodengine", "model/default_specular");
            Minecraft.getInstance().getTextureManager().register(defaultSpecularTexture, texture);
        }
        return defaultSpecularTexture;
    }

    /**
     * Frees every per-material texture uploaded so far. Called when the model registry is cleared, so
     * a resource reload doesn't strand the previous set of textures on the GPU. The shared
     * white/flat-normal/default-specular fallbacks are deliberately kept — one texel each, valid
     * across reloads.
     */
    public static void releaseAll() {
        for (ResourceLocation id : REGISTERED) {
            Minecraft.getInstance().getTextureManager().release(id);
        }
        REGISTERED.clear();
        // Cached render types hold a texture id each; dropping them together keeps a reloaded model
        // from drawing through a type bound to a texture that has just been freed.
        ModelRenderTypes.clearCache();
    }
}
