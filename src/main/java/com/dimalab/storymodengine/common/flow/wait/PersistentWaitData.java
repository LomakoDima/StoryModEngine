package com.dimalab.storymodengine.common.flow.wait;

import com.dimalab.storymodengine.api.capabilities.EntityCapability;

import java.util.HashMap;
import java.util.Map;

/**
 * Named deadlines for {@code wait_persistent}, keyed by the script's own {@code name} argument — a
 * per-player {@code @Capability}, persisted automatically like every other one in this engine.
 * Deliberately just a name, not {@code flowId + name} (a real, documented simplification): two
 * *different* wait names for the same player never collide, but two concurrent runs of the same flow
 * reusing the same literal name would — the same class of limitation HollowEngine's own bare-string
 * {@code stateTag()} keys have for {@code waitPersistent}.
 */
public final class PersistentWaitData implements EntityCapability {

    public Map<String, Long> deadlines = new HashMap<>();
}
