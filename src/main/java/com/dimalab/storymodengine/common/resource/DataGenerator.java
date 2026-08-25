package com.dimalab.storymodengine.common.resource;

import com.dimalab.storymodengine.common.content.ContentDescriptor;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.decoration.PaintingVariant;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates the {@code PackType.SERVER_DATA} counterpart to {@link AssetGenerator}: data a
 * discovered piece of content needs to actually function in the world, as opposed to render on
 * screen. Currently covers exactly one case — every discovered {@link PaintingVariant} is added
 * to vanilla's {@code minecraft:placeable} tag ({@code data/minecraft/tags/painting_variant/placeable.json}).
 * Without it, {@code Painting#create} (verified against source) only ever draws candidates from
 * that tag when a player places a painting item, so a registered variant absent from it would be a
 * real, loadable registry entry that no survival player could ever actually place.
 *
 * <p>Written the same way a mod author's own data pack would contribute to the tag — a {@code
 * "values"} array, {@code "replace": false} — so it merges with, rather than replaces, entries any
 * other pack (vanilla's own, another mod's, the world's own datapacks) contributes to the same tag.
 */
final class DataGenerator {

    private DataGenerator() {
    }

    /** Generates data-pack entries for every {@code modId}-namespaced descriptor. */
    static Map<ResourceLocation, byte[]> generate(String modId, List<ContentDescriptor<?>> discovered) {
        Map<ResourceLocation, byte[]> resources = new HashMap<>();

        JsonArray placeablePaintings = new JsonArray();
        for (ContentDescriptor<?> descriptor : discovered) {
            if (descriptor.id().getNamespace().equals(modId)
                    && PaintingVariant.class.isAssignableFrom(descriptor.contentType())) {
                placeablePaintings.add(descriptor.id().toString());
            }
        }
        if (placeablePaintings.size() > 0) {
            JsonObject tag = new JsonObject();
            tag.addProperty("replace", false);
            tag.add("values", placeablePaintings);
            resources.put(
                    ResourceLocation.fromNamespaceAndPath("minecraft", "tags/painting_variant/placeable.json"),
                    bytes(tag));
        }
        return resources;
    }

    private static byte[] bytes(JsonObject json) {
        return json.toString().getBytes(StandardCharsets.UTF_8);
    }
}
