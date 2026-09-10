package com.dimalab.storymodengine.common.scripting.persistence;

import com.dimalab.storymodengine.api.capabilities.EntityCapability;

import java.util.HashMap;
import java.util.Map;

/** Per-player story variables — every {@code "player."}-prefixed dotted name. Mutable POJO with a public no-arg constructor, matching {@code StoryPlayerData}'s exact shape — no hand-written NBT/network code needed, {@code SerializerRegistry} resolves {@code Map<String,SmeValue>} automatically. */
public final class StoryVariableData implements EntityCapability {

    public Map<String, SmeValue> vars = new HashMap<>();
}
