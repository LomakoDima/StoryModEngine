package com.dimalab.storymodengine.common.quest.json;

import com.dimalab.storymodengine.api.quest.reward.Reward;
import com.google.gson.JsonObject;

@FunctionalInterface
public interface RewardJsonParser {
    Reward parse(JsonObject o);
}
