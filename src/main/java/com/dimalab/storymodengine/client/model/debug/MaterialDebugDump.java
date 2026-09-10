package com.dimalab.storymodengine.client.model.debug;

import com.dimalab.storymodengine.client.model.pbr.LabPbrConverter;
import com.dimalab.storymodengine.common.logging.EngineLog;
import com.dimalab.storymodengine.common.model.MaterialData;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Writes every material's converted LabPBR normal/specular map to {@code <gameDir>/
 * storymodengine-debug/materials/<model>/material_<n>_n.png}/{@code _s.png} for direct inspection —
 * the same "temporary debug aid, write intermediate artifacts to disk under the game directory,
 * best-effort, swallow write failures" pattern {@code OculusSMECompat}'s own {@code
 * dumpMergedSourceForDebugging} already established for merged shader source, copied here rather
 * than invented fresh. Backing {@link ModelCommand}'s {@code storymodengine model materials <name>}.
 *
 * <p>Runs the exact same {@link LabPbrConverter} conversion the real render path uses (via {@code
 * ModelTextureLoader}) — this dump exists specifically so a wrong channel value (a backwards green
 * flip, a metalness value that landed in LabPBR's "predefined real metal" band) is visible by opening
 * a PNG, rather than only inferable from how a model looks lit under a shader pack.
 */
final class MaterialDebugDump {

    private MaterialDebugDump() {
    }

    /** @return how many materials were successfully dumped (partial failures on individual materials are logged, not thrown). */
    static int dumpAll(String modelName, List<MaterialData> materials) {
        Path dir = Minecraft.getInstance().gameDirectory.toPath().resolve("storymodengine-debug").resolve("materials").resolve(modelName);
        int dumped = 0;
        for (int i = 0; i < materials.size(); i++) {
            if (dumpOne(dir, i, materials.get(i))) {
                dumped++;
            }
        }
        return dumped;
    }

    private static boolean dumpOne(Path dir, int index, MaterialData material) {
        try {
            Files.createDirectories(dir);
            try (NativeImage normal = buildNormal(material)) {
                normal.writeToFile(dir.resolve("material_" + index + "_n.png"));
            }
            try (NativeImage specular = buildSpecular(material)) {
                specular.writeToFile(dir.resolve("material_" + index + "_s.png"));
            }
            return true;
        } catch (Exception e) {
            EngineLog.channel("Model").warn("Could not dump debug PBR textures for material " + index + " of '" + dir.getFileName() + "' (non-fatal)", e);
            return false;
        }
    }

    private static NativeImage buildNormal(MaterialData material) throws IOException {
        if (material.normal() == null) {
            NativeImage flat = new NativeImage(1, 1, false);
            flat.setPixelRGBA(0, 0, net.minecraft.util.FastColor.ABGR32.color(255, 255, 128, 128));
            return flat;
        }
        try (NativeImage gltfNormal = NativeImage.read(new ByteArrayInputStream(material.normal().image()));
             NativeImage occlusion = material.occlusion() != null ? NativeImage.read(new ByteArrayInputStream(material.occlusion().image())) : null) {
            return LabPbrConverter.convertNormalMap(gltfNormal, occlusion);
        }
    }

    private static NativeImage buildSpecular(MaterialData material) throws IOException {
        NativeImage metallicRoughness = null;
        NativeImage emissive = null;
        try {
            if (material.metallicRoughness() != null) {
                metallicRoughness = NativeImage.read(new ByteArrayInputStream(material.metallicRoughness().image()));
            }
            if (material.emissive() != null) {
                emissive = NativeImage.read(new ByteArrayInputStream(material.emissive().image()));
            }
            return LabPbrConverter.convertSpecularMap(metallicRoughness, material.metallicFactor(), material.roughnessFactor(),
                    emissive, material.emissiveFactor(), 1, 1);
        } finally {
            if (metallicRoughness != null) {
                metallicRoughness.close();
            }
            if (emissive != null) {
                emissive.close();
            }
        }
    }
}
