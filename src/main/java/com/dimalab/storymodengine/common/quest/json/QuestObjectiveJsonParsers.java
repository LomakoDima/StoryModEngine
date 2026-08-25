package com.dimalab.storymodengine.common.quest.json;

import com.dimalab.storymodengine.common.quest.objective.Objective;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.util.GsonHelper;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code "type"} → {@link ObjectiveJsonParser} — a lookup map, **not a switch inside the loader**
 * (task §12: "не создавай giant enum + switch"). {@link #register} is how a mod author's own custom
 * {@code Objective} gets a JSON shape too, the same extensibility guarantee the Java DSL already has.
 *
 * <p>{@code "event"}/custom objectives are deliberately not registered here — an arbitrary {@code
 * BiPredicate<ServerPlayer,E>} can't be expressed as JSON data, the exact same "not representable in
 * JSON, by design" carve-out {@code CutsceneJsonLoader} documents for {@code EventTrack}/{@code
 * ActionTrack}.
 */
public final class QuestObjectiveJsonParsers {

    private static final Map<String, ObjectiveJsonParser> PARSERS = new ConcurrentHashMap<>();

    static {
        register("kill", o -> Objective.kill(GsonHelper.getAsString(o, "entity"), GsonHelper.getAsInt(o, "count", 1)));
        register("collect", o -> Objective.collect(GsonHelper.getAsString(o, "item"), GsonHelper.getAsInt(o, "count", 1)));
        register("talkTo", o -> Objective.talkTo(GsonHelper.getAsString(o, "entity")));
        register("interact", o -> Objective.interact(GsonHelper.getAsString(o, "block")));
        register("location", o -> Objective.location(
                new BlockPos(GsonHelper.getAsInt(o, "x"), GsonHelper.getAsInt(o, "y"), GsonHelper.getAsInt(o, "z")),
                GsonHelper.getAsFloat(o, "radius", 2f)));
        register("findEntity", o -> Objective.findEntity(GsonHelper.getAsString(o, "entity"), GsonHelper.getAsFloat(o, "radius", 8f)));
        register("dialogue", o -> Objective.dialogue(GsonHelper.getAsString(o, "dialogue")));
        register("quest", o -> Objective.quest(GsonHelper.getAsString(o, "quest")));
    }

    private QuestObjectiveJsonParsers() {
    }

    public static void register(String type, ObjectiveJsonParser parser) {
        PARSERS.put(type, parser);
    }

    public static Objective parse(JsonObject o) {
        String type = GsonHelper.getAsString(o, "type");
        ObjectiveJsonParser parser = PARSERS.get(type);
        if (parser == null) {
            throw new IllegalArgumentException("Unknown objective type '" + type + "' — expected one of " + PARSERS.keySet()
                    + ", or register a custom one via QuestObjectiveJsonParsers.register(...)");
        }
        return parser.parse(o);
    }
}
