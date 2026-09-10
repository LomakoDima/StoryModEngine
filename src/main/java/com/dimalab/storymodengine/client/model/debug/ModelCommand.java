package com.dimalab.storymodengine.client.model.debug;

import com.dimalab.storymodengine.client.model.LazyModelLoader;
import com.dimalab.storymodengine.client.model.ModelInstance;
import com.dimalab.storymodengine.common.StoryModEngine;
import com.dimalab.storymodengine.common.event.Subscription;
import com.dimalab.storymodengine.common.logging.EngineLog;
import com.dimalab.storymodengine.common.model.ModelDefinition;
import com.dimalab.storymodengine.common.model.ModelLoadState;
import com.dimalab.storymodengine.common.model.ModelRegistry;
import com.dimalab.storymodengine.common.model.MaterialData;
import com.dimalab.storymodengine.common.model.Primitive;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * {@code /sme model list|load|test|selftest|materials} — mirrors {@code
 * raycast.example.RaycastDemoCommand}'s {@code test|debug|selftest} shape. A client command (see
 * MODEL_SYSTEM_DESIGN.md's command surface): model loading/rendering is entirely client-side, so
 * there's no {@code ServerPlayer} for a server command to act on.
 *
 * <p>{@code load}/{@code test} take a short bare name (e.g. {@code test}, not a full resource path)
 * — the same {@code StringArgumentType.word()} + "assume the {@code storymodengine} namespace"
 * convention {@code trigger.example.TriggerDemoCommand}/{@code dialogue.example.DialogueDemoCommand}
 * already use, rather than {@code ResourceLocationArgument} (which defaults an unqualified name to
 * the {@code minecraft} namespace and treats it as a literal resource path — the exact mismatch that
 * produced "Could not read minecraft:test" the first time this command shipped). The name is resolved
 * against the same {@code storymodengine/models/} directory {@code ModelReloadListener} scans, trying
 * {@code .gltf} then {@code .glb}, so a model registered by a real {@code /reload} and one force-
 * loaded via {@code /model load} end up under the identical {@link ResourceLocation} key.
 */
@Mod.EventBusSubscriber(modid = StoryModEngine.MODID, value = Dist.CLIENT)
public final class ModelCommand {

    private static final String DIRECTORY = "storymodengine/models";
    private static final String[] EXTENSIONS = {".gltf", ".glb"};

    private ModelCommand() {
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("sme")
                .then(Commands.literal("model")
                        .then(Commands.literal("list").executes(context -> list()))
                        .then(Commands.literal("load")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(context -> load(StringArgumentType.getString(context, "name")))))
                        .then(Commands.literal("test")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(context -> test(StringArgumentType.getString(context, "name"), null))
                                        .then(Commands.argument("animation", StringArgumentType.word())
                                                .executes(context -> test(StringArgumentType.getString(context, "name"), StringArgumentType.getString(context, "animation"))))))
                        .then(Commands.literal("selftest").executes(context -> selftest()))
                        .then(Commands.literal("materials")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(context -> materials(StringArgumentType.getString(context, "name")))))));
    }

    /**
     * Shows every loaded model with its full stats, then every id {@code /reload} found on disk but
     * nothing has asked to load yet — models are parsed on demand now (see {@link LazyModelLoader}),
     * so most of what's on disk is normally in this second group until something spawns it.
     */
    private static int list() {
        var models = ModelRegistry.all();
        var knownIds = ModelRegistry.knownIds();
        if (models.isEmpty() && knownIds.isEmpty()) {
            message("No models found — drop a .gltf/.glb under assets/storymodengine/models/ and /reload.");
            return 0;
        }
        for (ModelDefinition model : models) {
            message(describe(model.id().toString(), model));
        }
        int notYetLoaded = 0;
        for (ResourceLocation id : knownIds) {
            if (ModelRegistry.get(id) == null) {
                message(id + " — not loaded yet (/sme model load <name>, or spawn an NPC wearing it)");
                notYetLoaded++;
            }
        }
        return models.size() + notYetLoaded;
    }

    /**
     * Delegates the actual off-thread read+parse to {@link LazyModelLoader#request} (the same
     * on-demand path {@code NpcEntity} triggers on spawn — see its own doc) instead of driving its own
     * {@code Async} chain, which fixed a real gap this command used to have: going through {@code
     * GltfModelParser.parse} directly here, rather than {@code ModelLoading.load}, meant a model
     * loaded via this command never picked up its own {@code .smemeta} (rig/aliases/animation
     * controller) — {@link LazyModelLoader} always goes through {@code ModelLoading.load}, so that
     * asymmetry with a real {@code /reload} is gone.
     *
     * <p>Subscribes to the model's {@link ModelLoadState} flow rather than the old one-shot {@code
     * thenMain}/{@code onFailure} pair; since a {@code StateFlow} replays its current value
     * immediately, an already-loaded model reports success right away without re-parsing anything, and
     * the listener unsubscribes itself the moment it sees a terminal ({@code LOADED}/{@code FAILED})
     * state so repeated {@code /sme model load} calls don't accumulate listeners.
     */
    private static int load(String name) {
        ResourceLocation resolved = resolveOnDisk(name);
        if (resolved == null) {
            message("No model named '" + name + "' under assets/storymodengine/" + DIRECTORY + "/ (tried .gltf and .glb)");
            return 0;
        }
        Subscription[] subscription = new Subscription[1];
        subscription[0] = LazyModelLoader.stateFor(resolved).subscribe(state -> {
            if (state == ModelLoadState.LOADED) {
                message("Loaded " + describe(name, ModelRegistry.get(resolved)));
                subscription[0].unsubscribe();
            } else if (state == ModelLoadState.FAILED) {
                message("Failed to load " + resolved + " — see log for details.");
                subscription[0].unsubscribe();
            }
        });
        LazyModelLoader.request(resolved);
        message("Loading " + resolved + "...");
        return 1;
    }

    private static String describe(String label, ModelDefinition model) {
        int primitiveCount = 0;
        int joints = 0;
        for (var node : model.allNodes()) {
            if (node.mesh() != null) {
                primitiveCount += node.mesh().primitives().size();
            }
            if (node.skin() != null) {
                joints = Math.max(joints, node.skin().jointCount());
            }
        }
        StringBuilder animations = new StringBuilder();
        for (var clip : model.animations()) {
            if (animations.length() > 0) {
                animations.append(", ");
            }
            animations.append(clip.name()).append(" (").append(String.format("%.2fs", clip.duration())).append(")");
        }
        return label + " — " + model.allNodes().size() + " node(s), " + model.meshCount() + " mesh(es), "
                + primitiveCount + " primitive(s), " + (joints > 0 ? joints + " joint(s), " : "no skin, ")
                + model.animations().size() + " animation(s)"
                + (animations.length() > 0 ? ": " + animations : "");
    }

    private static int test(String name, String animation) {
        ModelDefinition definition = resolveRegistered(name);
        if (definition == null) {
            message("No such loaded model: '" + name + "' (try /sme model load " + name + " first)");
            return 0;
        }
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            message("No player to anchor the test render to.");
            return 0;
        }
        ModelInstance instance = new ModelInstance(definition);
        if (animation != null && instance.play(animation, true) == null) {
            message("Model has no animation named '" + animation + "' — showing the bind pose instead.");
            animation = null;
        }
        ModelDebugRenderStage.show(instance, player.position().add(player.getLookAngle().scale(3)));
        message("Showing '" + name + "'" + (animation != null ? " playing '" + animation + "'" : " (bind pose)") + " — look towards where you were facing.");
        return 1;
    }

    private static int selftest() {
        List<ModelSelfTest.Result> results = ModelSelfTest.runAll();
        int passed = 0;
        for (ModelSelfTest.Result result : results) {
            message((result.passed() ? "[PASS] " : "[FAIL] ") + result.name() + (result.passed() ? "" : " — " + result.detail()));
            if (result.passed()) {
                passed++;
            }
        }
        message(passed + "/" + results.size() + " checks passed.");
        return passed;
    }

    /**
     * Dumps every distinct material the model actually uses (walked from its nodes' primitives, not a
     * separate materials list — this engine doesn't keep one) as converted LabPBR PNGs under {@code
     * <gameDir>/sme-debug/materials/<name>/} via {@link MaterialDebugDump} — see that
     * class's own doc for why this is the same debug-artifact pattern {@code OculusSMECompat} already
     * established, not a new mechanism.
     */
    private static int materials(String name) {
        ModelDefinition definition = resolveRegistered(name);
        if (definition == null) {
            message("No such loaded model: '" + name + "' (try /sme model load " + name + " first)");
            return 0;
        }
        Set<MaterialData> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        List<MaterialData> materials = new ArrayList<>();
        for (var node : definition.allNodes()) {
            if (node.mesh() == null) {
                continue;
            }
            for (Primitive primitive : node.mesh().primitives()) {
                MaterialData material = primitive.material();
                if (seen.add(material)) {
                    materials.add(material);
                }
            }
        }
        if (materials.isEmpty()) {
            message("'" + name + "' has no materials.");
            return 0;
        }
        int dumped = MaterialDebugDump.dumpAll(name, materials);
        message("Dumped " + dumped + "/" + materials.size() + " material(s) to <gameDir>/storymodengine-debug/materials/" + name + "/");
        return dumped;
    }

    /** Tries {@code storymodengine/models/<name>.gltf} then {@code .glb} against the actual resource manager — used by {@code load}, which reads bytes off disk/pack itself. */
    private static ResourceLocation resolveOnDisk(String name) {
        for (String extension : EXTENSIONS) {
            ResourceLocation candidate = new ResourceLocation(StoryModEngine.MODID, DIRECTORY + "/" + name + extension);
            if (Minecraft.getInstance().getResourceManager().getResource(candidate).isPresent()) {
                return candidate;
            }
        }
        return null;
    }

    /** Same resolution, but against {@link ModelRegistry} — used by {@code test}, since a model already registered by a real {@code /reload} doesn't need its file to be re-checked. */
    private static ModelDefinition resolveRegistered(String name) {
        for (String extension : EXTENSIONS) {
            ModelDefinition definition = ModelRegistry.get(new ResourceLocation(StoryModEngine.MODID, DIRECTORY + "/" + name + extension));
            if (definition != null) {
                return definition;
            }
        }
        return null;
    }

    private static void message(String text) {
        EngineLog.channel("Model").info(text);
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(Component.literal(text), false);
        }
    }
}
