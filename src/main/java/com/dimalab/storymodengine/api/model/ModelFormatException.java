package com.dimalab.storymodengine.api.model;

import net.minecraft.resources.ResourceLocation;

/**
 * Unchecked — carries the failing resource id so a catch site ({@code ModelReloadListener}, {@code
 * /sme model load}) can report exactly which file and why, without a checked-exception
 * ripple through every accessor/importer method in {@code common.model.gltf}.
 */
public final class ModelFormatException extends RuntimeException {

    private final ResourceLocation source;

    public ModelFormatException(ResourceLocation source, String message) {
        super(source + ": " + message);
        this.source = source;
    }

    public ModelFormatException(ResourceLocation source, String message, Throwable cause) {
        super(source + ": " + message, cause);
        this.source = source;
    }

    public ResourceLocation source() {
        return source;
    }
}
