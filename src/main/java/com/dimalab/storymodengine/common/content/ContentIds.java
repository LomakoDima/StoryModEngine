package com.dimalab.storymodengine.common.content;

import java.util.Locale;

/**
 * Derives a Forge-registry-safe id from a Java field name. Kept as its own component so the
 * naming convention can evolve (or be overridden per-type in the future) without touching
 * {@link ContentDiscovery}'s scanning/registration logic.
 */
final class ContentIds {

    private ContentIds() {
    }

    /**
     * Converts a conventional {@code UPPER_SNAKE_CASE} field name (e.g. {@code EXAMPLE_ITEM})
     * into a registry path (e.g. {@code example_item}).
     */
    static String fromFieldName(String fieldName) {
        return fieldName.toLowerCase(Locale.ROOT);
    }
}
