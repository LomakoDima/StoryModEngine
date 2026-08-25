package com.dimalab.storymodengine.common.quest.json;

import com.dimalab.storymodengine.common.logging.EngineLog;
import com.dimalab.storymodengine.common.quest.Quest;
import com.dimalab.storymodengine.common.quest.QuestDefinition;
import com.dimalab.storymodengine.common.quest.QuestRegistry;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.Map;

/**
 * Loads {@code QuestDefinition}s from {@code data/<namespace>/storymodengine/quests/*.json} — the
 * same {@code SimpleJsonResourceReloadListener} mechanism {@code DialogueJsonLoader}/{@code
 * CutsceneJsonLoader} already use, parsed through the *same* {@link Quest}/{@link QuestDefinition
 * .Builder} the Java DSL goes through (one validation path), then registered into the same {@link
 * QuestRegistry} {@code @AutoQuest} uses — no difference between a code-defined and a JSON-defined
 * quest from the rest of the system's point of view.
 */
public final class QuestJsonLoader extends SimpleJsonResourceReloadListener {

    public QuestJsonLoader() {
        super(new Gson(), "storymodengine/quests");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager resourceManager, ProfilerFiller profiler) {
        int loaded = 0;
        for (Map.Entry<ResourceLocation, JsonElement> entry : resources.entrySet()) {
            ResourceLocation id = entry.getKey();
            try {
                QuestDefinition definition = parse(id, entry.getValue().getAsJsonObject());
                QuestRegistry.register(definition);
                loaded++;
            } catch (Exception e) {
                EngineLog.channel("Quest").error("Failed to parse quest '" + id + "' — skipping", e);
            }
        }
        EngineLog.channel("Quest").info("[JSON] Loaded {} quest(s) from data packs", loaded);
    }

    private static boolean hasNonNull(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull();
    }

    public static QuestDefinition parse(ResourceLocation id, JsonObject root) {
        QuestDefinition.Builder builder = Quest.define(id);
        if (hasNonNull(root, "title")) {
            builder.title(GsonHelper.getAsString(root, "title"));
        }
        if (hasNonNull(root, "description")) {
            builder.description(GsonHelper.getAsString(root, "description"));
        }
        for (JsonElement e : GsonHelper.getAsJsonArray(root, "prerequisites", new JsonArray())) {
            builder.prerequisite(e.getAsString());
        }
        for (JsonElement e : GsonHelper.getAsJsonArray(root, "objectives", new JsonArray())) {
            builder.objective(QuestObjectiveJsonParsers.parse(e.getAsJsonObject()));
        }
        for (JsonElement e : GsonHelper.getAsJsonArray(root, "rewards", new JsonArray())) {
            builder.reward(QuestRewardJsonParsers.parse(e.getAsJsonObject()));
        }
        return builder.build();
    }
}
