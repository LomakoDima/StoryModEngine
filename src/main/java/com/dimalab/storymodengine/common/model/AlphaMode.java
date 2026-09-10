package com.dimalab.storymodengine.common.model;

/**
 * glTF's {@code alphaMode} per spec: {@code OPAQUE} (alpha ignored, fully opaque), {@code MASK}
 * (hard cutout at the material's own {@link MaterialData#alphaCutoff()}), {@code BLEND} (real
 * alpha-over translucency). See {@code ModelRenderTypes} for how each maps to render state, and
 * {@code PbrUniformBinder} for how {@code MASK}'s cutoff reaches the one shader this engine compiles
 * itself (the two paths that reuse a vanilla-compiled shader can't be given a configurable cutoff —
 * a documented, pre-existing limitation, not something this enum's addition changes).
 */
public enum AlphaMode {
    OPAQUE,
    MASK,
    BLEND
}
