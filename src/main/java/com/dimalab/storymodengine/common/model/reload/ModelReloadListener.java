package com.dimalab.storymodengine.common.model.reload;

import com.dimalab.storymodengine.common.logging.EngineLog;
import com.dimalab.storymodengine.common.model.ModelRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.Set;

/**
 * Discovers every {@code assets/<ns>/storymodengine/models/*.gltf}/{@code *.glb} on each client
 * resource reload — registered via {@link ModelReloadBootstrap} on {@code
 * RegisterClientReloadListenersEvent} (not {@code AddReloadListenerEvent} — see
 * MODEL_SYSTEM_DESIGN.md's Lifecycle section for why that would have been the wrong event for
 * client-only content). Extends {@code SimplePreparableReloadListener} rather than {@code
 * SimpleJsonResourceReloadListener} for the same reason {@code scripting.reload.SmeReloadListener}
 * does — a {@code .glb} is not JSON.
 *
 * <p><b>Discovers, doesn't parse.</b> An earlier version read and parsed every model's full geometry
 * here, synchronously, unconditionally, on every reload — regardless of whether anything currently
 * spawned uses it. This version only lists which model ids exist (cheap: no byte reads, no glTF
 * decode) so {@code /sme model list} can still show what's <i>available</i>, and resets
 * {@link ModelRegistry} so stale definitions from before the reload don't linger. Actually parsing a
 * model's geometry now happens on demand, the first time something asks for it — see {@code
 * client.model.LazyModelLoader}.
 *
 * <p><b>Deliberately does not use {@code common.concurrent.Async}</b> — a resource reload's {@code
 * prepare} stage can run (and, for the client's own initial reload, always does run) before {@code
 * Async}'s thread pools exist at all — they're created on {@code ServerStartingEvent} (see {@code
 * concurrent.executor.AsyncExecutors}'s own doc: {@code isAccepting()} is {@code false} until then),
 * which hasn't fired yet during the client's boot-time resource load. Listing resource ids (unlike the
 * old full read+parse this replaced) is cheap enough that this was never really the bottleneck
 * {@code Async} would have addressed here anyway — {@code LazyModelLoader} is where the actual
 * off-thread work now happens, and only once a player is in a world (well after {@code
 * ServerStartingEvent}), same reasoning {@code client.model.debug.ModelCommand} already relied on.
 */
public final class ModelReloadListener extends SimplePreparableReloadListener<Set<ResourceLocation>> {

    private static final String DIRECTORY = "storymodengine/models";

    @Override
    protected Set<ResourceLocation> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        return Set.copyOf(resourceManager.listResources(DIRECTORY,
                loc -> loc.getPath().endsWith(".gltf") || loc.getPath().endsWith(".glb")).keySet());
    }

    @Override
    protected void apply(Set<ResourceLocation> knownIds, ResourceManager resourceManager, ProfilerFiller profiler) {
        ModelRegistry.reset();
        ModelRegistry.setKnownIds(knownIds);
        EngineLog.channel("Model").info("[reload] {} model(s) available, loaded on demand", knownIds.size());
    }
}
