package com.dimalab.storymodengine.client.model;

import com.dimalab.storymodengine.client.model.gpu.BatchTangentBuffers;
import com.dimalab.storymodengine.client.model.gpu.BatchTangentCollector;
import com.dimalab.storymodengine.client.model.gpu.InstanceBatchCollector;
import com.dimalab.storymodengine.client.model.gpu.InstanceFlush;
import com.dimalab.storymodengine.client.model.pbr.PbrUniformBinder;
import com.dimalab.storymodengine.common.StoryModEngine;
import com.dimalab.storymodengine.common.model.ModelRegistry;
import com.dimalab.storymodengine.common.model.physics.ModelPhysics;
import com.dimalab.storymodengine.common.model.reload.ModelReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Registers {@link ModelReloadListener} on the client's own resource-pack reload — {@code
 * RegisterClientReloadListenersEvent}, not {@code AddReloadListenerEvent} (that one is documented on
 * its own class, and verified against source, as server-resources-only; see MODEL_SYSTEM_DESIGN.md's
 * Lifecycle section). Self-registers via {@code @Mod.EventBusSubscriber} the same way {@code
 * dialogue.json.DialogueJsonBootstrap}/{@code scripting.reload.SmeReloadBootstrap} do — no explicit
 * call from {@code EngineBootstrap} needed.
 *
 * <p>Lives under {@code client} rather than next to the listener it registers, because it wires up
 * {@link ModelTextureLoader} — which touches genuinely client-only types. The listener itself stays
 * in {@code common}: it only reads bytes and builds format-independent model data, with nothing
 * client-only in it.
 */
@Mod.EventBusSubscriber(modid = StoryModEngine.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ModelReloadBootstrap {

    private ModelReloadBootstrap() {
    }

    @SubscribeEvent
    public static void onRegisterClientReloadListeners(RegisterClientReloadListenersEvent event) {
        // A callback rather than a direct call, so common.model never references the client-only
        // texture code — see ModelRegistry#onReset. Derived physics (bounds, collision shapes) is
        // dropped at the same moment: an edited model must re-derive its hitbox, not keep the old one.
        // The derived chain is dropped together, in dependency order: physics is derived from parsed
        // models, textures belong to their materials. Live instances then rebuild themselves, because
        // they compare the definition they were built from against the one that resolves now.
        // InstanceFlush's per-Primitive GL buffers are keyed by Primitive identity, and a reload parses
        // brand-new Primitive objects for every model — without dropping the old ones here, their GPU
        // buffers would leak on every single reload rather than only on GC (the already-documented gap
        // for GpuSkinBuffers' WeakHashMap eviction).
        ModelRegistry.onReset(() -> {
            ModelPhysics.clearCache();
            ModelTextureLoader.releaseAll();
            InstanceFlush.clearCache();
            InstanceBatchCollector.clearCache();
            BatchTangentBuffers.clearCache();
            BatchTangentCollector.clearCache();
            PbrUniformBinder.clearCache();
            // Without this, a model that had already reached LOADED before this reset stays stuck
            // reporting LOADED forever after — LazyModelLoader#request treats that as "nothing to
            // do" and never re-fires, even though the ModelDefinition it pointed at was just wiped
            // above. See LazyModelLoader#clearStates' own doc for the exact symptom this caused.
            LazyModelLoader.clearStates();
        });
        event.registerReloadListener(new ModelReloadListener());
    }
}
