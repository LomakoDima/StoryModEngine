package com.dimalab.storymodengine.common.scripting.example;

import com.dimalab.storymodengine.common.scripting.registry.ZoneRegistry;
import net.minecraft.core.BlockPos;

/**
 * The one named zone {@code prologue.sme}'s {@code when player enters "village_gate"} trigger
 * references — SME's grammar has no coordinate literal (see {@code ZoneRegistry}'s own doc), so a
 * real zone has to be registered from Java for the demo to validate and compile at all. Called from
 * {@code ScriptingBootstrap.init}, before the first {@code AddReloadListenerEvent}-driven {@code
 * .sme} reload ever runs.
 */
public final class DemoZones {

    private DemoZones() {
    }

    public static void register() {
        ZoneRegistry.register("village_gate", new BlockPos(0, 64, 0), 10);
    }
}
