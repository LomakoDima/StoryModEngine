package com.dimalab.storymodengine.common.quest;

import net.minecraft.resources.ResourceLocation;

/** The DSL entry point — {@code Quest.define(id)...build()}, mirroring {@code CutsceneDefinition.define(id)}/{@code DialogueDefinition.builder(id)}. A separate one-method class rather than folding this into {@code QuestDefinition} itself purely so the call site reads {@code Quest.define(...)}, matching the task's own requested developer experience. */
public final class Quest {

    private Quest() {
    }

    public static QuestDefinition.Builder define(ResourceLocation id) {
        return QuestDefinition.builder(id);
    }
}
