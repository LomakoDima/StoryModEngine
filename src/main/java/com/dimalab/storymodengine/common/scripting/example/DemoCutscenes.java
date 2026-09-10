package com.dimalab.storymodengine.common.scripting.example;

import com.dimalab.storymodengine.api.cinematic.annotation.AutoCutscene;
import com.dimalab.storymodengine.common.cinematic.CutsceneDefinition;
import net.minecraft.resources.ResourceLocation;

/**
 * The one hand-written cutscene {@code prologue.sme}'s {@code play cinematic village_welcome} step
 * references — full {@code cinematic <id> { ... }} authoring is out of SME's own MVP scope, so the
 * demo needs a real, Java-authored definition to reference, exactly the pattern the design doc's
 * fallback describes.
 */
public final class DemoCutscenes {

    @AutoCutscene
    public static final CutsceneDefinition VILLAGE_WELCOME = CutsceneDefinition.define(new ResourceLocation("storymodengine", "village_welcome"))
            .duration(60)
            .shot(0, 60, camera -> camera
                    .position(0, new org.joml.Vector3f(0, 0, 0))
                    .position(60, new org.joml.Vector3f(0, 0, 0))
                    .rotation(0, new org.joml.Quaternionf())
                    .rotation(60, new org.joml.Quaternionf()))
            .subtitle("You have proven yourself to the village.", 0, 60)
            .build();

    private DemoCutscenes() {
    }
}
