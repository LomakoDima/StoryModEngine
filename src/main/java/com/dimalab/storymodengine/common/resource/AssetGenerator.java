package com.dimalab.storymodengine.common.resource;

import com.dimalab.storymodengine.common.content.ContentDescriptor;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraftforge.fluids.FluidType;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Infers the minimal client resources a discovered piece of content needs — blockstate, block
 * model, item model — purely from its {@link ContentDescriptor}: the same id and type information
 * already used to register it, nothing declared twice. This is the boundary described in
 * {@code ARCHITECTURE.md}: {@link com.dimalab.storymodengine.common.content.ContentDiscovery} produces
 * descriptors (Content Discovery → Registration → Resource Description); this class turns those
 * descriptors into resource-pack bytes (→ Generated Resources). Neither side depends on the
 * mod author declaring anything about resources.
 *
 * <p>Textures are never generated here: they're real files the mod author supplies at the
 * conventional path ({@code assets/<modid>/textures/(item|block)/<id>.png}) — this only emits the
 * JSON that points at that path. If the file isn't there, Minecraft falls back to its normal
 * missing-texture placeholder, exactly as it would for a hand-authored model with a bad texture
 * reference; that's a content-authoring concern, not something the engine can (or should) invent.
 *
 * <p>{@link LiquidBlock}s are skipped entirely: fluids render from {@code FluidState} via their
 * {@code FluidType}, not from a blockstate/model — see {@code AutoFluidType} in the {@code
 * content} package for how their textures are resolved instead.
 *
 * <p>A {@link ParticleType} gets a {@code particles/<id>.json} listing its single conventional
 * texture ({@code textures/particle/<id>.png}) — the file {@code RegisterParticleProvidersEvent}
 * requires to exist before a sprite-based provider can be registered for it (verified against
 * {@code ParticleDescriptionProvider} source). {@code PaintingVariant} needs no client resource of
 * its own beyond its texture (no model, no lang key in 1.20.1) — see {@code DataGenerator} for the
 * one thing it does need, a server-side tag entry.
 *
 * <p>A single {@code lang/en_us.json} is generated alongside the per-descriptor files, one entry
 * per descriptor whose type has a real Minecraft translation key ({@code Block}, {@code Item},
 * {@code MobEffect}, {@code FluidType} — see {@link #langKey}), the display text inferred by
 * title-casing the id ({@code ruby_block → "Ruby Block"}). A {@code BlockItem} is skipped: its
 * {@code getDescriptionId()} delegates to its block (verified against vanilla source, not
 * assumed), so generating a separate, never-read {@code item.<modid>.<id>} entry for it would be
 * dead weight — the block's own entry already covers what's shown.
 */
final class AssetGenerator {

    private AssetGenerator() {
    }

    /** Generates resource-pack entries for every {@code modId}-namespaced descriptor. */
    static Map<ResourceLocation, byte[]> generate(String modId, List<ContentDescriptor<?>> discovered) {
        Set<ResourceLocation> blockIds = new HashSet<>();
        for (ContentDescriptor<?> descriptor : discovered) {
            if (isOwnNamespace(descriptor, modId) && isModeledBlock(descriptor)) {
                blockIds.add(descriptor.id());
            }
        }

        Map<ResourceLocation, byte[]> resources = new HashMap<>();
        JsonObject lang = new JsonObject();
        for (ContentDescriptor<?> descriptor : discovered) {
            if (!isOwnNamespace(descriptor, modId)) {
                continue;
            }
            String path = descriptor.id().getPath();
            if (isModeledBlock(descriptor)) {
                resources.put(assetPath(modId, "blockstates/" + path + ".json"), blockstateJson(modId, path));
                resources.put(assetPath(modId, "models/block/" + path + ".json"), blockModelJson(modId, path));
            } else if (Item.class.isAssignableFrom(descriptor.contentType())) {
                byte[] itemModel = blockIds.contains(descriptor.id())
                        ? blockItemModelJson(modId, path)
                        : plainItemModelJson(modId, path);
                resources.put(assetPath(modId, "models/item/" + path + ".json"), itemModel);
            } else if (ParticleType.class.isAssignableFrom(descriptor.contentType())) {
                resources.put(assetPath(modId, "particles/" + path + ".json"), particleJson(modId, path));
            }

            String key = langKey(descriptor.contentType(), modId, path);
            if (key != null) {
                lang.addProperty(key, titleCase(path));
            }
        }
        if (lang.size() > 0) {
            resources.put(assetPath(modId, "lang/en_us.json"), bytes(lang));
        }
        return resources;
    }

    private static boolean isOwnNamespace(ContentDescriptor<?> descriptor, String modId) {
        return descriptor.id().getNamespace().equals(modId);
    }

    private static boolean isModeledBlock(ContentDescriptor<?> descriptor) {
        return Block.class.isAssignableFrom(descriptor.contentType())
                && !LiquidBlock.class.isAssignableFrom(descriptor.contentType());
    }

    private static ResourceLocation assetPath(String modId, String path) {
        return ResourceLocation.fromNamespaceAndPath(modId, path);
    }

    private static byte[] blockstateJson(String modId, String path) {
        JsonObject variant = new JsonObject();
        variant.addProperty("model", modId + ":block/" + path);
        JsonObject variants = new JsonObject();
        variants.add("", variant);
        JsonObject root = new JsonObject();
        root.add("variants", variants);
        return bytes(root);
    }

    private static byte[] blockModelJson(String modId, String path) {
        JsonObject textures = new JsonObject();
        textures.addProperty("all", modId + ":block/" + path);
        JsonObject root = new JsonObject();
        root.addProperty("parent", "minecraft:block/cube_all");
        root.add("textures", textures);
        return bytes(root);
    }

    private static byte[] blockItemModelJson(String modId, String path) {
        JsonObject root = new JsonObject();
        root.addProperty("parent", modId + ":block/" + path);
        return bytes(root);
    }

    private static byte[] plainItemModelJson(String modId, String path) {
        JsonObject textures = new JsonObject();
        textures.addProperty("layer0", modId + ":item/" + path);
        JsonObject root = new JsonObject();
        root.addProperty("parent", "minecraft:item/generated");
        root.add("textures", textures);
        return bytes(root);
    }

    /**
     * A particle description with a single texture at the conventional
     * {@code textures/particle/<id>.png} — the same one-texture shape
     * {@code net.minecraftforge.common.data.ParticleDescriptionProvider#sprite} produces (verified
     * against source), required for any {@code ParticleType} registered for use with {@code
     * RegisterParticleProvidersEvent#registerSprite}/{@code #registerSpriteSet}.
     */
    private static byte[] particleJson(String modId, String path) {
        JsonArray textures = new JsonArray();
        textures.add(modId + ":" + path);
        JsonObject root = new JsonObject();
        root.add("textures", textures);
        return bytes(root);
    }

    /**
     * The vanilla translation key {@code contentType} actually reads via its own
     * {@code getDescriptionId()} — {@code "block."}/{@code "item."}/{@code "effect."}/
     * {@code "fluid_type."} + {@code modId} + {@code "."} + {@code path}, matching
     * {@code Util.makeDescriptionId} exactly — or {@code null} for a type with no translation key
     * of its own (a raw {@code Fluid}, whose name comes from its {@code FluidType} instead) or one
     * already covered by a sibling descriptor ({@code BlockItem}, covered by its {@code Block}).
     */
    private static String langKey(Class<?> contentType, String modId, String path) {
        if (BlockItem.class.isAssignableFrom(contentType)) {
            return null;
        }
        String prefix;
        if (Block.class.isAssignableFrom(contentType)) {
            prefix = "block";
        } else if (Item.class.isAssignableFrom(contentType)) {
            prefix = "item";
        } else if (MobEffect.class.isAssignableFrom(contentType)) {
            prefix = "effect";
        } else if (FluidType.class.isAssignableFrom(contentType)) {
            prefix = "fluid_type";
        } else {
            return null;
        }
        return prefix + "." + modId + "." + path;
    }

    /** {@code ruby_block -> "Ruby Block"} — the same word-by-word title-casing datagen language providers typically start from before manual overrides. */
    private static String titleCase(String path) {
        String[] words = path.split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (result.length() > 0) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                result.append(word.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return result.toString();
    }

    private static byte[] bytes(JsonObject json) {
        return json.toString().getBytes(StandardCharsets.UTF_8);
    }
}
