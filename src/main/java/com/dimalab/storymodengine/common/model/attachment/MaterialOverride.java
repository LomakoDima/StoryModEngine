package com.dimalab.storymodengine.common.model.attachment;

/**
 * What one named material of an entity's model should show instead of its own baked texture — the
 * value type held in {@link MaterialOverrides#byName}, not itself a capability. Mirrors HollowEngine's
 * real, shipped {@code MaterialSource} (`common/models/MaterialSource.kt`, confirmed via its own test
 * suite, `ModelMaterialTests.kt` — not the earlier session's dead AI-component scaffolding), collapsed
 * from a sealed type into one flat class since this engine's capability serializer resolves by
 * concrete class, not polymorphically: whichever of {@link #texture}/{@link #skinPlayer} is non-empty
 * wins, {@link #texture} first.
 *
 * <p>Scoped to base-color texture + the existing player-skin convenience only — HE's optional
 * {@code normal}/{@code specular} map override is the same mechanism and a cheap follow-up; its
 * {@code color} tint needs a second hook (a shader uniform/vertex-color path, not just texture
 * selection) this pass doesn't touch.
 */
public final class MaterialOverride {

    /** A real, namespaced {@code ResourceLocation} string (e.g. {@code "mypack:textures/entity/guard.png"}); empty = unset. */
    public String texture = "";

    /** A real player's name or UUID, resolved via {@code PlayerSkinSource}; empty = unset. Only consulted when {@link #texture} is empty. */
    public String skinPlayer = "";
}
