package com.dimalab.storymodengine.client.model;

import com.dimalab.storymodengine.common.concurrent.Async;
import com.dimalab.storymodengine.common.concurrent.StateFlow;
import com.dimalab.storymodengine.common.logging.EngineLog;
import com.dimalab.storymodengine.common.model.ModelDefinition;
import com.dimalab.storymodengine.common.model.ModelLoadState;
import com.dimalab.storymodengine.common.model.ModelLoading;
import com.dimalab.storymodengine.common.model.ModelRegistry;
import com.dimalab.storymodengine.common.model.physics.ModelPhysics;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The client's on-demand counterpart to the old eager {@code ModelReloadListener} behaviour: a
 * model's full geometry is only ever parsed once something actually asks for it, via {@link #request}
 * (fire-and-forget) or {@link #resolveOrRequest} (read-current-and-kick-off-if-needed) — not, as
 * before, unconditionally for every {@code .gltf}/{@code .glb} on every {@code /reload} regardless of
 * whether anything currently uses it. Exposes each model's progress as a {@link StateFlow} rather than
 * a bare synchronous result, since the parse itself runs off-thread (see {@link Async}) and a caller
 * may want to react the instant it finishes rather than poll {@link ModelRegistry} every frame.
 *
 * <p>Client-only, deliberately: {@code common.model.physics.ModelPhysics}'s own separate classpath
 * fallback (which the server side uses for collision, and which is already synchronous by necessity —
 * a hitbox can't wait on an async load) is untouched by this class and stays exactly as it was. This
 * class lives in {@code client.model} rather than {@code common.model} specifically because it reaches
 * {@link Minecraft#getInstance()} directly, which {@code common.model} deliberately stays free of (see
 * MODEL_SYSTEM_DESIGN.md).
 */
public final class LazyModelLoader {

    /** Same two extensions {@code ModelPhysics}/{@code ModelCommand} each already try in this order — not shared as a constant across the three, matching how those two don't share it with each other either. */
    private static final String[] EXTENSIONS = {".gltf", ".glb"};

    private static final Map<ResourceLocation, StateFlow<ModelLoadState>> STATES = new ConcurrentHashMap<>();

    /**
     * Caches the {@link ResourceLocation}+extension a bare model name resolved to, so {@link
     * #resolveOrRequestByName}/{@link #requestByName} don't have to re-ask {@code ResourceManager
     * #getResource} — a real lookup into the whole layered resource-pack stack, not a cheap field read
     * — every single time they're called. This was a genuine, measured hot-path bug: {@link
     * #resolveOrRequestByName} is called once per NPC per frame from {@code GltfModelLayer.draw}, so
     * with 100 NPCs on screen that was 100+ resource-existence lookups every frame for a mapping that
     * never changes once a model has actually been found — confirmed as the dominant per-frame cost in
     * a 100-NPC stress test (~200µs per {@code GltfModelLayer.draw} call, the overwhelming majority of
     * it spent here rather than in posing or drawing). A name that resolves to nothing is deliberately
     * not cached — that outcome depends on which resource packs are loaded, which {@link #clearStates}
     * already re-evaluates on the next reload, and a missing model is rare enough on the hot path that
     * caching its absence isn't worth the extra bookkeeping.
     */
    private static final Map<String, ResourceLocation> NAME_TO_ID_CACHE = new ConcurrentHashMap<>();

    private LazyModelLoader() {
    }

    /**
     * Drops every tracked load state — must run whenever {@link ModelRegistry#reset()} does (wired in
     * {@code ModelReloadBootstrap}'s own {@code ModelRegistry#onReset} callback, alongside {@code
     * ModelPhysics.clearCache()}/{@code ModelTextureLoader.releaseAll()}/{@code
     * InstanceFlush.clearCache()}). Without this, a model that reached {@code LOADED} before a reload
     * stays stuck at {@code LOADED} in {@link #STATES} forever after — {@link #request} treats
     * {@code LOADED} as "nothing to do" and never re-fires, even though {@code ModelRegistry.reset()}
     * just wiped the actual {@link ModelDefinition} it was pointing at. The visible symptom is an NPC
     * that rendered fine right up until a resource reload (e.g. F3+T), then simply stops rendering —
     * {@code ModelRegistry.get(id)} correctly returns {@code null} post-reload, but {@code request(id)}
     * silently refuses to reload it because its stale {@code StateFlow} still reads {@code LOADED}.
     *
     * <p>Also clears {@link #NAME_TO_ID_CACHE} — a reload can change which resource pack (and therefore
     * which extension, or whether a name resolves at all) backs a given model name.
     */
    public static void clearStates() {
        STATES.clear();
        NAME_TO_ID_CACHE.clear();
    }

    /**
     * Bare-name convenience for a caller that only knows a model's name, not its exact resource id or
     * extension (e.g. an NPC's own {@code modelName()} — see {@code client.entity.NpcModelPreloader}):
     * checks which extension actually exists, then {@link #request}s that one. A silent no-op if
     * neither does — the same "nothing to preload" outcome a normal render would also reach.
     */
    public static void requestByName(String name) {
        ResourceLocation cached = NAME_TO_ID_CACHE.get(name);
        if (cached != null) {
            request(cached);
            return;
        }
        for (String extension : EXTENSIONS) {
            ResourceLocation candidate = ModelPhysics.idFor(name, extension);
            if (Minecraft.getInstance().getResourceManager().getResource(candidate).isPresent()) {
                NAME_TO_ID_CACHE.put(name, candidate);
                request(candidate);
                return;
            }
        }
    }

    /**
     * The bare-name counterpart to {@link #resolveOrRequest}, for a rendering path that only has a
     * model name to go on (see {@code GltfModelLayer#instanceFor}) — deliberately <b>not</b> {@code
     * ModelPhysics.resolve}, which this replaces on the client render path specifically: that method's
     * own classpath fallback is a synchronous parse, which would defeat the entire point of loading
     * lazily and asynchronously here. {@code ModelPhysics.resolve} keeps that fallback for its other,
     * still-synchronous-by-necessity callers (server-side collision, and the client's own {@code
     * ModelEntityRenderer}/{@code ModelBlockRenderer}, neither of which this change touches).
     */
    public static ModelDefinition resolveOrRequestByName(String name) {
        ResourceLocation cached = NAME_TO_ID_CACHE.get(name);
        if (cached != null) {
            return resolveOrRequest(cached);
        }
        for (String extension : EXTENSIONS) {
            ResourceLocation candidate = ModelPhysics.idFor(name, extension);
            if (Minecraft.getInstance().getResourceManager().getResource(candidate).isPresent()) {
                NAME_TO_ID_CACHE.put(name, candidate);
                return resolveOrRequest(candidate);
            }
        }
        return null;
    }

    /** This model's load state, creating a fresh {@code NOT_LOADED} flow on first mention — never {@code null}. */
    public static StateFlow<ModelLoadState> stateFor(ResourceLocation id) {
        return STATES.computeIfAbsent(id, k -> new StateFlow<>(ModelLoadState.NOT_LOADED));
    }

    /**
     * Kicks off the load if it hasn't started yet (or failed before) — a no-op while already {@code
     * LOADING} or once {@code LOADED}. Fire-and-forget: a caller that cares about the outcome watches
     * {@link #stateFor}, not a return value from here.
     */
    public static void request(ResourceLocation id) {
        StateFlow<ModelLoadState> state = stateFor(id);
        ModelLoadState current = state.value();
        if (current == ModelLoadState.LOADING || current == ModelLoadState.LOADED) {
            return;
        }
        state.set(ModelLoadState.LOADING);
        ResourceManager resourceManager = Minecraft.getInstance().getResourceManager();
        Async.io()
                .supply(() -> readBytes(id, resourceManager))
                .thenApply(bytes -> ModelLoading.load(id, bytes, readSidecarBytes(id, resourceManager), resourceManager))
                .thenMain(definition -> {
                    ModelRegistry.register(definition);
                    state.set(ModelLoadState.LOADED);
                })
                .onFailure(throwable -> {
                    EngineLog.channel("Model").error("Failed to load " + id, throwable);
                    state.set(ModelLoadState.FAILED);
                });
    }

    /**
     * Already registered → returned immediately, no state change. Otherwise {@link #request}ed and
     * {@code null} returned — every existing caller already tolerates "no definition yet" ({@link
     * ModelInstanceStore#instanceFor} returns {@code null} for a {@code null} definition, and both
     * renderers already skip a frame when that happens), so this doesn't need a placeholder object.
     */
    public static ModelDefinition resolveOrRequest(ResourceLocation id) {
        ModelDefinition registered = ModelRegistry.get(id);
        if (registered != null) {
            return registered;
        }
        request(id);
        return null;
    }

    private static byte[] readBytes(ResourceLocation id, ResourceManager resourceManager) {
        try (InputStream in = resourceManager.open(id)) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + id, e);
        }
    }

    private static byte[] readSidecarBytes(ResourceLocation id, ResourceManager resourceManager) {
        ResourceLocation sidecarId = ModelLoading.metadataIdFor(id);
        Optional<Resource> sidecar = resourceManager.getResource(sidecarId);
        if (sidecar.isEmpty()) {
            return null;
        }
        try (InputStream in = sidecar.get().open()) {
            return in.readAllBytes();
        } catch (IOException e) {
            EngineLog.channel("Model").error("Failed to read " + sidecarId, e);
            return null;
        }
    }
}
