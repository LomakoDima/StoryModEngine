package com.dimalab.storymodengine.common.model;

import com.dimalab.storymodengine.common.logging.EngineLog;
import com.dimalab.storymodengine.common.model.rig.RigBone;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parses a {@code .smemeta} sidecar. Shape:
 *
 * <pre>{@code
 * {
 *   "facing": "auto",
 *   "rig": [
 *     { "name": "root",    "anchor": "body" },
 *     { "name": "head",    "parent": "root", "anchor": "golova",
 *       "members": ["golova", "Helmet", "leftBrow", "rightBrow"] }
 *   ]
 * }
 * }</pre>
 *
 * <p>A bone's {@code anchor} names the source node whose own origin is that joint's pivot — pivots
 * are read from real geometry rather than written as coordinates, so the description survives the
 * model being re-exported at a different offset or scale.
 *
 * <p>Malformed metadata is reported and skipped rather than thrown: a bad sidecar should cost the
 * model its rig, not its geometry — the same "log and keep going per resource" rule {@code
 * DialogueJsonLoader} already follows.
 */
public final class ModelMetadataLoader {

    private static final Gson GSON = new Gson();

    private ModelMetadataLoader() {
    }

    public static ModelMetadata parse(ResourceLocation source, byte[] bytes) {
        try {
            JsonObject root = GSON.fromJson(new String(bytes, StandardCharsets.UTF_8), JsonObject.class);
            if (root == null) {
                return ModelMetadata.EMPTY;
            }
            return new ModelMetadata(parseRig(source, root), parseFacing(source, root), parseAliases(source, root),
                    parseSkinMaterialIndex(source, root), parseAnimationController(root), parseDisableCulling(root),
                    parseHitboxExcludeNodes(source, root), parseForceRenderPath(source, root));
        } catch (Exception e) {
            EngineLog.channel("Model").error("Failed to parse metadata for " + source + " — the model will load without it", e);
            return ModelMetadata.EMPTY;
        }
    }

    private static ModelMetadata.Facing parseFacing(ResourceLocation source, JsonObject root) {
        if (!root.has("facing") || root.get("facing").isJsonNull()) {
            return ModelMetadata.Facing.AUTO;
        }
        String raw = GsonHelper.getAsString(root, "facing").toUpperCase(Locale.ROOT);
        try {
            return ModelMetadata.Facing.valueOf(raw);
        } catch (IllegalArgumentException e) {
            EngineLog.channel("Model").warn("{}: unknown facing '{}' — expected auto/gltf/minecraft; using auto", source, raw);
            return ModelMetadata.Facing.AUTO;
        }
    }

    private static List<RigBone> parseRig(ResourceLocation source, JsonObject root) {
        if (!root.has("rig") || root.get("rig").isJsonNull()) {
            return List.of();
        }
        JsonArray bones = GsonHelper.getAsJsonArray(root, "rig");
        List<RigBone> parsed = new ArrayList<>(bones.size());
        for (JsonElement element : bones) {
            JsonObject bone = element.getAsJsonObject();
            String name = GsonHelper.getAsString(bone, "name");
            String parent = bone.has("parent") && !bone.get("parent").isJsonNull()
                    ? GsonHelper.getAsString(bone, "parent") : null;
            String anchor = GsonHelper.getAsString(bone, "anchor");

            List<String> members = new ArrayList<>();
            if (bone.has("members") && !bone.get("members").isJsonNull()) {
                for (JsonElement member : GsonHelper.getAsJsonArray(bone, "members")) {
                    members.add(member.getAsString());
                }
            }
            parsed.add(new RigBone(name, parent, anchor, List.copyOf(members)));
        }
        EngineLog.channel("Model").debug("{}: rig with {} bone(s)", source, parsed.size());
        return List.copyOf(parsed);
    }

    /** {@code "skinMaterial": 0} — a 0-based index into the glTF's own {@code materials} array, since Blockbench-exported materials often have no {@code name} at all to alias by. */
    private static Integer parseSkinMaterialIndex(ResourceLocation source, JsonObject root) {
        if (!root.has("skinMaterial") || root.get("skinMaterial").isJsonNull()) {
            return null;
        }
        try {
            return GsonHelper.getAsInt(root, "skinMaterial");
        } catch (Exception e) {
            EngineLog.channel("Model").warn("{}: 'skinMaterial' must be a material index (integer) — ignoring", source);
            return null;
        }
    }

    /** {@code "animationController": "storymodengine:standard_player"} — a Java-registered preset id, see {@code client.model.animator.AnimatorPresets}. */
    private static String parseAnimationController(JsonObject root) {
        if (!root.has("animationController") || root.get("animationController").isJsonNull()) {
            return null;
        }
        return GsonHelper.getAsString(root, "animationController");
    }

    /** {@code "disableCulling": true} — see {@link ModelMetadata}'s own {@code @param} doc. Defaults to {@code false}, same as every other optional key here. */
    private static boolean parseDisableCulling(JsonObject root) {
        if (!root.has("disableCulling") || root.get("disableCulling").isJsonNull()) {
            return false;
        }
        try {
            return GsonHelper.getAsBoolean(root, "disableCulling");
        } catch (Exception e) {
            EngineLog.channel("Model").warn("'disableCulling' must be true/false — ignoring");
            return false;
        }
    }

    /** {@code "hitboxExcludeNodes": ["LeftArm", "RightArm"]} — this model's own node names, no fixed vocabulary. */
    private static List<String> parseHitboxExcludeNodes(ResourceLocation source, JsonObject root) {
        if (!root.has("hitboxExcludeNodes") || root.get("hitboxExcludeNodes").isJsonNull()) {
            return List.of();
        }
        List<String> parsed = new ArrayList<>();
        for (JsonElement element : GsonHelper.getAsJsonArray(root, "hitboxExcludeNodes")) {
            parsed.add(element.getAsString());
        }
        EngineLog.channel("Model").debug("{}: {} hitbox-excluded node(s)", source, parsed.size());
        return List.copyOf(parsed);
    }

    /** {@code "forceRenderPath": "pipeline"} — see {@link ModelMetadata#forceRenderPath}'s own doc. Case-insensitive; an unrecognized value is reported and treated as absent (the automatic heuristic applies), not a hard failure. */
    private static RenderPath parseForceRenderPath(ResourceLocation source, JsonObject root) {
        if (!root.has("forceRenderPath") || root.get("forceRenderPath").isJsonNull()) {
            return null;
        }
        String raw = GsonHelper.getAsString(root, "forceRenderPath").toUpperCase(Locale.ROOT);
        try {
            return RenderPath.valueOf(raw);
        } catch (IllegalArgumentException e) {
            EngineLog.channel("Model").warn("{}: unknown forceRenderPath '{}' — expected pipeline/batching; using the automatic heuristic", source, raw);
            return null;
        }
    }

    private static Map<String, String> parseAliases(ResourceLocation source, JsonObject root) {
        if (!root.has("aliases") || root.get("aliases").isJsonNull()) {
            return Map.of();
        }
        JsonObject aliases = GsonHelper.getAsJsonObject(root, "aliases");
        Map<String, String> parsed = new LinkedHashMap<>();
        for (String engineName : aliases.keySet()) {
            parsed.put(engineName, aliases.get(engineName).getAsString());
        }
        EngineLog.channel("Model").debug("{}: {} alias(es)", source, parsed.size());
        return Map.copyOf(parsed);
    }
}
