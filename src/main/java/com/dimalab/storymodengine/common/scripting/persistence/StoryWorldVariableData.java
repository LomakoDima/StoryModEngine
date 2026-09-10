package com.dimalab.storymodengine.common.scripting.persistence;

import com.dimalab.storymodengine.api.capabilities.LevelCapability;

import java.util.HashMap;
import java.util.Map;

/** Global/world story variables — every dotted name without a {@code "player."} prefix (e.g. {@code village.reputation}). Same mutable-POJO shape as {@link StoryVariableData}, attached per {@code Level} instead of per player. */
public final class StoryWorldVariableData implements LevelCapability {

    public Map<String, SmeValue> vars = new HashMap<>();
}
