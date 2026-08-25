package com.dimalab.storymodengine.common.voxel;

import com.dimalab.storymodengine.common.logging.EngineLog;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Reads a block model's {@code elements} (cuboids only, see class doc on {@link ShapeRotation}
 * for the rotation half of this) straight off the classpath — deliberately <em>not</em> through
 * {@code Minecraft.getInstance().getResourceManager()}. {@code Block#getShape()} runs on both
 * logical sides, including a dedicated server where no {@code Minecraft} instance exists; every
 * mod's {@code assets/} folder is already on the shared mod classloader's classpath on both sides,
 * so a plain {@code getResourceAsStream} resolves identically everywhere. The trade-off, accepted
 * for this foundation version: a resource pack that overrides a model at runtime is invisible here
 * (there's no reload listener involved) — this reads whatever's on the classpath at JVM start,
 * which for a normal mod jar is exactly the model it ships.
 *
 * <p>Never throws: a missing or malformed model is a content-authoring problem, not something that
 * should crash world/collision code. Every failure path logs through
 * {@code EngineLog.channel("Voxel")} and returns {@link ShapeDefinition#EMPTY}.
 */
final class ModelShapeLoader {

    private static final int MAX_PARENT_DEPTH = 8;

    private ModelShapeLoader() {
    }

    static ShapeDefinition load(ResourceLocation modelId) {
        return load(modelId, 0);
    }

    private static ShapeDefinition load(ResourceLocation modelId, int depth) {
        if (depth >= MAX_PARENT_DEPTH) {
            EngineLog.channel("Voxel").warn("Model {} exceeds max parent depth ({}) — possible cyclic parent chain, stopping.",
                    modelId, MAX_PARENT_DEPTH);
            return ShapeDefinition.EMPTY;
        }

        String classpathPath = "assets/" + modelId.getNamespace() + "/models/" + modelId.getPath() + ".json";
        JsonObject root = readJson(classpathPath, modelId);
        if (root == null) {
            return ShapeDefinition.EMPTY;
        }

        if (root.has("elements")) {
            return parseElements(root.getAsJsonArray("elements"), modelId);
        }

        if (root.has("parent")) {
            ResourceLocation parentId = new ResourceLocation(root.get("parent").getAsString());
            return load(parentId, depth + 1);
        }

        EngineLog.channel("Voxel").debug("Model {} has no elements and no parent — empty shape.", modelId);
        return ShapeDefinition.EMPTY;
    }

    private static JsonObject readJson(String classpathPath, ResourceLocation modelId) {
        try (InputStream stream = ModelShapeLoader.class.getClassLoader().getResourceAsStream(classpathPath)) {
            if (stream == null) {
                EngineLog.channel("Voxel").warn("Model {} not found on classpath ({}).", modelId, classpathPath);
                return null;
            }
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        } catch (IOException | RuntimeException e) {
            EngineLog.channel("Voxel").error("Malformed model json for {}: {}", modelId, e.getMessage());
            return null;
        }
    }

    private static ShapeDefinition parseElements(JsonArray elements, ResourceLocation modelId) {
        ShapeDefinition.Builder builder = ShapeDefinition.builder();
        int skippedRotated = 0;
        for (int i = 0; i < elements.size(); i++) {
            JsonObject element = elements.get(i).getAsJsonObject();
            if (element.has("rotation")) {
                skippedRotated++;
                continue;
            }
            double[] from = readVec3(element.getAsJsonArray("from"));
            double[] to = readVec3(element.getAsJsonArray("to"));
            builder.box(from[0], from[1], from[2], to[0], to[1], to[2]);
        }
        if (skippedRotated > 0) {
            EngineLog.channel("Voxel").warn(
                    "Model {} has {} rotated element(s) — skipped (Phase 1 supports axis-aligned elements only).",
                    modelId, skippedRotated);
        }
        return builder.build();
    }

    private static double[] readVec3(JsonArray array) {
        return new double[]{array.get(0).getAsDouble(), array.get(1).getAsDouble(), array.get(2).getAsDouble()};
    }
}
