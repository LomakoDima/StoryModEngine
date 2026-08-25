package com.dimalab.storymodengine.common.dialogue.json;

import com.dimalab.storymodengine.common.dialogue.DialogueDefinition;
import com.dimalab.storymodengine.common.dialogue.registry.DialogueRegistry;
import com.dimalab.storymodengine.common.logging.EngineLog;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.Map;

/**
 * Loads {@code DialogueDefinition}s from {@code data/<namespace>/storymodengine/dialogues/*.json} —
 * the same {@code SimpleJsonResourceReloadListener} mechanism {@code
 * cinematic.json.CutsceneJsonLoader} already established, registered via {@code
 * AddReloadListenerEvent} in {@link DialogueJsonBootstrap}. Parses into the *same* {@link
 * DialogueDefinition.Builder} the Java DSL uses — one validation path, not a parallel one — then
 * registers into {@link DialogueRegistry} exactly like {@code @AutoDialogue} does.
 *
 * <p><b>Not representable in JSON, by design</b>: {@link com.dimalab.storymodengine.api.dialogue.DialogueCommand}
 * (arbitrary {@code Consumer<DialogueContext>}) and {@link com.dimalab.storymodengine.common.dialogue.DialogueConditionEntry}
 * (arbitrary {@code Evaluator<Boolean>}) are genuinely Java code, not data — a JSON node is a list of
 * {@code lines} followed by an optional {@code choices} list (each choice a plain jump, no
 * condition/command) or a single unconditional {@code jump}. A dialogue that needs commands or
 * conditions is authored in Java instead, the same boundary the JSON cutscene format already draws
 * around {@code EventTrack}/{@code ActionTrack}.
 */
public final class DialogueJsonLoader extends SimpleJsonResourceReloadListener {

    public DialogueJsonLoader() {
        super(new Gson(), "storymodengine/dialogues");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager resourceManager, ProfilerFiller profiler) {
        int loaded = 0;
        for (Map.Entry<ResourceLocation, JsonElement> entry : resources.entrySet()) {
            ResourceLocation id = entry.getKey();
            try {
                DialogueDefinition definition = parse(id, entry.getValue().getAsJsonObject());
                DialogueRegistry.register(definition);
                loaded++;
            } catch (Exception e) {
                EngineLog.channel("Dialogue").error("Failed to parse dialogue '" + id + "' — skipping", e);
            }
        }
        EngineLog.channel("Dialogue").info("[JSON] Loaded {} dialogue(s) from data packs", loaded);
    }

    /** See {@code CutsceneJsonLoader}'s identical helper: {@code o.has(key)} alone is true even for an explicit JSON {@code null}. */
    private static boolean hasNonNull(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull();
    }

    /** Public so a future {@code dialogue playjson}-style command (mirroring {@code CutscenePlayJsonCommand}) can parse a raw file the same way, without a full data pack reload. */
    public static DialogueDefinition parse(ResourceLocation id, JsonObject root) {
        DialogueDefinition.Builder builder = DialogueDefinition.builder(id);
        if (hasNonNull(root, "title")) {
            builder.title(GsonHelper.getAsString(root, "title"));
        }

        JsonObject nodes = GsonHelper.getAsJsonObject(root, "nodes");
        String startId = GsonHelper.getAsString(root, "start");

        // The declared start node first, so the builder's own "first .node() call is the start
        // node" rule matches the JSON's explicit "start" field, then every remaining node.
        parseNode(builder, startId, nodes.getAsJsonObject(startId));
        for (Map.Entry<String, JsonElement> entry : nodes.entrySet()) {
            if (entry.getKey().equals(startId)) {
                continue;
            }
            parseNode(builder, entry.getKey(), entry.getValue().getAsJsonObject());
        }

        return builder.build();
    }

    private static void parseNode(DialogueDefinition.Builder builder, String nodeId, JsonObject nodeJson) {
        builder.node(nodeId);

        if (hasNonNull(nodeJson, "lines")) {
            for (JsonElement lineElement : nodeJson.getAsJsonArray("lines")) {
                JsonObject lineJson = lineElement.getAsJsonObject();
                builder.line(GsonHelper.getAsString(lineJson, "speaker", ""), GsonHelper.getAsString(lineJson, "text"));
            }
        }

        if (hasNonNull(nodeJson, "choices")) {
            for (JsonElement choiceElement : nodeJson.getAsJsonArray("choices")) {
                JsonObject choiceJson = choiceElement.getAsJsonObject();
                String choiceId = hasNonNull(choiceJson, "id") ? GsonHelper.getAsString(choiceJson, "id") : null;
                builder.choice(choiceId, GsonHelper.getAsString(choiceJson, "text"));
                if (hasNonNull(choiceJson, "target")) {
                    builder.gotoNode(GsonHelper.getAsString(choiceJson, "target"));
                }
            }
        } else if (hasNonNull(nodeJson, "jump")) {
            builder.jump(GsonHelper.getAsString(nodeJson, "jump"));
        } else {
            builder.end();
        }
    }
}
