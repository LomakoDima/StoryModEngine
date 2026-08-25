package com.dimalab.storymodengine.common.content;

import net.minecraft.resources.ResourceLocation;

import java.util.function.Supplier;

/**
 * Everything the engine inferred about one {@link AutoContent} field: its registry id, the
 * concrete type of content it supplies, and the supplier used to construct it. Captured once
 * during discovery ({@link ContentDiscovery#getDiscovered()}) so future engine subsystems —
 * generated resources (models, blockstates, language entries, loot tables, ...), content lookups,
 * and similar — can reuse the same information instead of re-scanning the mod's classes or asking
 * the mod author to declare anything twice.
 */
public record ContentDescriptor<T>(ResourceLocation id, Class<T> contentType, Supplier<T> supplier) {
}
