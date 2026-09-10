package com.dimalab.storymodengine.common.cinematic.example;

import com.dimalab.storymodengine.common.StoryModEngine;
import com.dimalab.storymodengine.common.cinematic.CutsceneDefinition;
import com.dimalab.storymodengine.api.cinematic.annotation.AutoCutscene;
import net.minecraft.resources.ResourceLocation;

import static com.dimalab.storymodengine.common.cinematic.CutsceneDefinition.pos;
import static com.dimalab.storymodengine.common.cinematic.CutsceneDefinition.rot;

/**
 * A small, genuinely static {@code @AutoCutscene} definition — purely to show the declarative API
 * cleanly (see {@code ARCHITECTURE.md}). This is <b>not</b> what {@code /sme cutscene
 * demo} plays: a real cutscene almost always needs positions relative to wherever it's triggered,
 * which a field built once at mod-load time (before any world exists) fundamentally can't express —
 * {@link CutsceneDemoCommand} instead builds its definition fresh, per invocation, from the
 * player's actual position, and registers it manually via {@code CutsceneRegistry.register(...)},
 * the same coexisting manual path {@code FlowRegistry.register(...)} already has alongside
 * {@code @AutoFlow}.
 */
public final class CutsceneExamples {

    @AutoCutscene
    public static final CutsceneDefinition SIMPLE_EXAMPLE = CutsceneDefinition.define(id("simple_example"))
            .duration(60)
            .shot(0, 60, camera -> camera
                    .position(0, pos(0, 70, 0))
                    .position(60, pos(0, 70, 10))
                    .rotation(0, rot(0, 0)))
            .subtitle("Hello, world.", 10, 30)
            .build();

    private CutsceneExamples() {
    }

    static ResourceLocation id(String path) {
        return new ResourceLocation(StoryModEngine.MODID, path);
    }
}
