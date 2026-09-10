package com.dimalab.storymodengine.common.scripting;

import com.dimalab.storymodengine.common.scripting.command.StoryCommandDiscovery;
import com.dimalab.storymodengine.common.scripting.example.DemoZones;
import com.dimalab.storymodengine.common.scripting.reload.SmeHotReloadWatcher;
import net.minecraftforge.fml.ModLoadingContext;

/**
 * Called last from {@code EngineBootstrap.init} — SME needs Dialogue/Quest/Trigger/Cinematic/Flow/
 * Capability/Command registries to already exist, since its reload listener resolves references
 * against them. Runs {@link StoryCommandDiscovery} (mod-construction time, same timing as every
 * other {@code @Auto*} discovery) and arms {@link SmeHotReloadWatcher} (dev-environment-only; it
 * only actually starts watching on {@code ServerStartingEvent}). The {@code .sme} reload listener
 * itself wires in automatically via {@code reload.SmeReloadBootstrap}'s own {@code
 * @Mod.EventBusSubscriber} registration, so nothing else is needed here.
 */
public final class ScriptingBootstrap {

    private ScriptingBootstrap() {
    }

    public static void init() {
        String modId = ModLoadingContext.get().getContainer().getModId();
        StoryCommandDiscovery.run(modId);
        SmeHotReloadWatcher.registerOnce();
        // Demo-only wiring bundled with the engine itself, so prologue.sme validates and compiles
        // out of the box — a third-party mod would register its own zones from its own bootstrap.
        DemoZones.register();
    }
}
