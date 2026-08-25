package com.dimalab.storymodengine.common.quest.json;

import com.dimalab.storymodengine.api.quest.reward.Reward;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** {@code "type"} → {@link RewardJsonParser}, same registry shape as {@link QuestObjectiveJsonParsers}. {@code "action"} (a raw {@code Consumer<ServerPlayer>}) is Java-only, not registered here — {@link Reward#command} is its JSON-representable counterpart. */
public final class QuestRewardJsonParsers {

    private static final Map<String, RewardJsonParser> PARSERS = new ConcurrentHashMap<>();

    static {
        register("item", o -> Reward.item(GsonHelper.getAsString(o, "item"), GsonHelper.getAsInt(o, "count", 1)));
        register("experience", o -> Reward.experience(GsonHelper.getAsInt(o, "amount")));
        register("command", o -> Reward.command(GsonHelper.getAsString(o, "command")));
        register("unlockQuest", o -> Reward.unlockQuest(GsonHelper.getAsString(o, "quest")));
    }

    private QuestRewardJsonParsers() {
    }

    public static void register(String type, RewardJsonParser parser) {
        PARSERS.put(type, parser);
    }

    public static Reward parse(JsonObject o) {
        String type = GsonHelper.getAsString(o, "type");
        RewardJsonParser parser = PARSERS.get(type);
        if (parser == null) {
            throw new IllegalArgumentException("Unknown reward type '" + type + "' — expected one of " + PARSERS.keySet()
                    + ", or register a custom one via QuestRewardJsonParsers.register(...)");
        }
        return parser.parse(o);
    }
}
