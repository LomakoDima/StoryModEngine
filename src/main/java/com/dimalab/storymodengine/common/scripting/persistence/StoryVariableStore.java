package com.dimalab.storymodengine.common.scripting.persistence;

import com.dimalab.storymodengine.common.capabilities.Capabilities;
import com.dimalab.storymodengine.common.scripting.ast.SmeValueType;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

/**
 * The compiler-facing read/write facade over the two story-variable capabilities — a dotted name
 * starting with {@code "player."} routes to {@link StoryVariableData} (the player's own {@code
 * EntityCapability}); every other name routes to {@link StoryWorldVariableData} (a {@code
 * LevelCapability} on the player's current level). {@code village.reputation} is one literal key
 * string in that map, never nested field access.
 *
 * <p><b>Known MVP simplification</b>: a {@code var x = <initial>} declaration only establishes
 * {@code x}'s type for validation (see {@code validation.VariableUsagePass}) — it does not seed the
 * capability with that initial value at reload time. A never-{@code set} variable reads as its
 * type's natural zero value ({@code 0}/{@code false}/{@code 0.0}/{@code ""}) regardless of what
 * initial literal was authored. Every value in {@code prologue.sme} happens to declare a zero
 * initial, so this never produces an observably wrong result there; matching an arbitrary authored
 * initial (e.g. {@code var x = true}) is a documented fast-follow, not attempted in this pass.
 */
public final class StoryVariableStore {

    private StoryVariableStore() {
    }

    public static SmeValue get(ServerPlayer player, String dottedName, SmeValueType declaredType) {
        SmeValue value = varsFor(player, dottedName).get(dottedName);
        return value != null ? value : SmeValue.zeroOf(declaredType);
    }

    public static boolean getBool(ServerPlayer player, String dottedName) {
        return get(player, dottedName, SmeValueType.BOOL).boolVal();
    }

    public static void set(ServerPlayer player, String dottedName, SmeValue value) {
        varsFor(player, dottedName).put(dottedName, value);
        markDirty(player, dottedName);
    }

    public static void add(ServerPlayer player, String dottedName, SmeValue delta) {
        SmeValue current = get(player, dottedName, delta.type());
        set(player, dottedName, combine(current, delta, true));
    }

    public static void subtract(ServerPlayer player, String dottedName, SmeValue delta) {
        SmeValue current = get(player, dottedName, delta.type());
        set(player, dottedName, combine(current, delta, false));
    }

    private static SmeValue combine(SmeValue current, SmeValue delta, boolean add) {
        return switch (current.type()) {
            case INT -> SmeValue.ofInt(add ? current.intVal() + delta.intVal() : current.intVal() - delta.intVal());
            case DOUBLE -> SmeValue.ofDouble(add ? current.doubleVal() + delta.doubleVal() : current.doubleVal() - delta.doubleVal());
            case BOOL, STRING -> current; // rejected at validation time (VariableUsagePass) — defensive no-op
        };
    }

    private static Map<String, SmeValue> varsFor(ServerPlayer player, String dottedName) {
        if (dottedName.startsWith("player.")) {
            return Capabilities.get(player, StoryVariableData.class).vars;
        }
        return Capabilities.get(player.level(), StoryWorldVariableData.class).vars;
    }

    private static void markDirty(ServerPlayer player, String dottedName) {
        if (dottedName.startsWith("player.")) {
            Capabilities.markDirty(player, StoryVariableData.class);
        } else {
            Capabilities.markDirty(player.level(), StoryWorldVariableData.class);
        }
    }
}
