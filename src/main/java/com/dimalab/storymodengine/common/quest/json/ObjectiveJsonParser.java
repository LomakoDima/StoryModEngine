package com.dimalab.storymodengine.common.quest.json;

import com.dimalab.storymodengine.common.quest.objective.Objective;
import com.google.gson.JsonObject;

/** One entry in {@link QuestObjectiveJsonParsers}' type registry — the JSON-side extension point mirroring {@code Objective}'s own Java-side extensibility (task §12/§16: a custom objective type gets a custom parser, not a switch statement anywhere in this package). */
@FunctionalInterface
public interface ObjectiveJsonParser {
    Objective parse(JsonObject o);
}
