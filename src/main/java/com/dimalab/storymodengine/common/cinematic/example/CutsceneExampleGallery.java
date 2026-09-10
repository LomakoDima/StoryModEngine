package com.dimalab.storymodengine.common.cinematic.example;

import com.dimalab.storymodengine.common.StoryModEngine;
import com.dimalab.storymodengine.common.cinematic.CinematicManager;
import com.dimalab.storymodengine.common.cinematic.CutsceneCancelledEvent;
import com.dimalab.storymodengine.common.cinematic.CutsceneCompletedEvent;
import com.dimalab.storymodengine.common.cinematic.CutsceneDefinition;
import com.dimalab.storymodengine.common.cinematic.CutsceneTriggerEvent;
import com.dimalab.storymodengine.common.cinematic.registry.CutsceneRegistry;
import com.dimalab.storymodengine.common.flow.Flow;
import com.dimalab.storymodengine.common.flow.FlowManager;
import com.dimalab.storymodengine.common.flow.registry.FlowRegistry;
import com.dimalab.storymodengine.common.logging.EngineLog;
import com.dimalab.storymodengine.api.math.Angles;
import com.dimalab.storymodengine.api.math.curve.ArcLengthTable;
import com.dimalab.storymodengine.api.math.curve.CatmullRomSpline;
import com.dimalab.storymodengine.api.math.curve.Curve;
import com.dimalab.storymodengine.api.math.interp.Easing;
import com.dimalab.storymodengine.api.math.interp.Interpolators;
import com.dimalab.storymodengine.api.math.transform.Transform;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import static com.dimalab.storymodengine.common.cinematic.CutsceneDefinition.pos;
import static com.dimalab.storymodengine.common.cinematic.CutsceneDefinition.rot;

/**
 * {@code /sme cutscene play <name>} — a gallery of examples, deliberately ordered
 * simple → complex, meant to exercise as much of the {@code cinematic} system's real surface as
 * one command tree reasonably can: single-keyframe statics, multi-keyframe easing, FOV, subtitles
 * with speakers, audio cues, {@code ActorTrack} (position/rotation/visibility, including the
 * {@code step()} interpolator), multi-shot hard cuts, {@code Trigger} → {@code EventBus} with a
 * real observable gameplay reaction, and a live (not just compiled) {@code Flow} → cutscene →
 * {@code Flow} continuation. This is additional demonstration content alongside the required
 * {@code /sme cutscene demo} (left untouched, in {@link CutsceneDemoCommand}) — nothing
 * here changes any {@code cinematic} core class.
 *
 * <p>Every definition here is built fresh per invocation, relative to the player's live position —
 * see {@link CutsceneExamples}'s Javadoc for why a {@code @AutoCutscene} static field can't do
 * this. Any villagers spawned for an example are tracked in {@link #ACTIVE_EXAMPLES} and discarded
 * as soon as that player's current example completes or is cancelled, the same pattern {@link
 * CutsceneDemoCommand} established, generalized here to an arbitrary number of actors per example.
 */
@Mod.EventBusSubscriber(modid = StoryModEngine.MODID)
public final class CutsceneExampleGallery {

    private static final ResourceLocation TRIGGER_FLASH = id("example_trigger_flash");
    private static final ResourceLocation TRIGGER_EPIC_REVEAL = id("example_epic_reveal");
    private static final ResourceLocation FLOW_DEMO_FLOW_ID = id("example_flow_demo");

    private static final Map<String, Consumer<ServerPlayer>> EXAMPLES = new LinkedHashMap<>();
    private static final Map<UUID, PendingCleanup> ACTIVE_EXAMPLES = new ConcurrentHashMap<>();

    static {
        EXAMPLES.put("simple", CutsceneExampleGallery::playSimple);
        EXAMPLES.put("move", CutsceneExampleGallery::playMove);
        EXAMPLES.put("zoom", CutsceneExampleGallery::playZoom);
        EXAMPLES.put("talk", CutsceneExampleGallery::playTalk);
        EXAMPLES.put("actor", CutsceneExampleGallery::playActor);
        EXAMPLES.put("shots", CutsceneExampleGallery::playShots);
        EXAMPLES.put("trigger", CutsceneExampleGallery::playTrigger);
        EXAMPLES.put("epic", CutsceneExampleGallery::playEpic);
        EXAMPLES.put("flow", CutsceneExampleGallery::playFlowIntegration);
        EXAMPLES.put("conversation", CutsceneExampleGallery::playConversation);
        EXAMPLES.put("flythrough", CutsceneExampleGallery::playFlythrough);
    }

    private CutsceneExampleGallery() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("sme")
                .then(Commands.literal("cutscene")
                        .then(Commands.literal("play")
                                .executes(CutsceneExampleGallery::listExamples)
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(EXAMPLES.keySet(), builder))
                                        .executes(CutsceneExampleGallery::play)))));
    }

    private static int listExamples(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        EngineLog.channel("Cinematic").info("Examples: {}", String.join(", ", EXAMPLES.keySet())).toChat(player);
        return 1;
    }

    private static int play(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(context, "name");
        Consumer<ServerPlayer> example = EXAMPLES.get(name);
        if (example == null) {
            EngineLog.channel("Cinematic").error(
                    "Unknown example '{}'. Try: {}", name, String.join(", ", EXAMPLES.keySet())).toChat(player);
            return 0;
        }
        example.accept(player);
        return 1;
    }

    // --- 1. the bare minimum: one static shot, nothing else ---

    private static void playSimple(ServerPlayer player) {
        Vec3 forward = player.getLookAngle();
        Vec3 shotPos = establishingShot(player, forward);
        float yaw = player.getYRot();
        CutsceneDefinition definition = CutsceneDefinition.define(id("example_simple"))
                .duration(40)
                .shot(0, 40, camera -> camera
                        .position(0, pos(shotPos.x, shotPos.y, shotPos.z))
                        .rotation(0, rot(yaw, 8)))
                .build();
        playAndRegister(player, definition, Map.of(), List.of());
    }

    /**
     * A vantage point clearly outside the player's own eyes — behind and above, angled slightly
     * down — so an example's very first frame is unmistakably a different camera, not just what
     * the player was already seeing. Used by every example whose shot doesn't already establish
     * its own distinct framing (e.g. {@code epic}, {@code shots}, and {@code demo} already do).
     */
    private static Vec3 establishingShot(ServerPlayer player, Vec3 forward) {
        return player.position().subtract(forward.scale(3)).add(0, 2.2, 0);
    }

    /** Yaw (Minecraft convention: degrees clockwise from south/+Z) a camera at {@code from} needs to face {@code to} — same formula shape as {@code Angles.toYawPitch}. */
    private static float yawTowards(Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    // --- 2. multi-keyframe position/rotation with per-segment easing ---

    private static void playMove(ServerPlayer player) {
        Vec3 forward = player.getLookAngle();
        Vec3 start = establishingShot(player, forward);
        Vec3 end = player.position().add(forward.scale(8)).add(0, 2, 0);
        float yaw = player.getYRot();
        CutsceneDefinition definition = CutsceneDefinition.define(id("example_move"))
                .duration(100)
                .shot(0, 100, camera -> camera
                        .position(0, pos(start.x, start.y, start.z), Interpolators.VECTOR3F.eased(Easing.EASE_IN_OUT_CUBIC))
                        .position(100, pos(end.x, end.y, end.z))
                        .rotation(0, rot(yaw, 10))
                        .rotation(100, rot(yaw, -10)))
                .build();
        playAndRegister(player, definition, Map.of(), List.of());
    }

    // --- 3. FOV as its own animated channel — a dramatic zoom in and back out ---

    private static void playZoom(ServerPlayer player) {
        Vec3 forward = player.getLookAngle();
        Vec3 shotPos = establishingShot(player, forward);
        float yaw = player.getYRot();
        CutsceneDefinition definition = CutsceneDefinition.define(id("example_zoom"))
                .duration(100)
                .shot(0, 100, camera -> camera
                        .position(0, pos(shotPos.x, shotPos.y, shotPos.z))
                        .rotation(0, rot(yaw, 8))
                        .fov(0, 70f)
                        .fov(50, 15f, Interpolators.FLOAT.eased(Easing.EASE_IN_OUT_QUAD))
                        .fov(100, 70f))
                .build();
        playAndRegister(player, definition, Map.of(), List.of());
    }

    // --- 4. SubtitleTrack (with speakers) + AudioTrack, several cues each ---

    private static void playTalk(ServerPlayer player) {
        Vec3 forward = player.getLookAngle();
        Vec3 shotPos = establishingShot(player, forward);
        float yaw = player.getYRot();
        CutsceneDefinition definition = CutsceneDefinition.define(id("example_talk"))
                .duration(160)
                .shot(0, 160, camera -> camera
                        .position(0, pos(shotPos.x, shotPos.y, shotPos.z))
                        .rotation(0, rot(yaw, 8)))
                .sound(0, SoundEvents.NOTE_BLOCK_BELL.value(), 1f, 1f)
                .subtitle("The path ahead splits in two.", 10, 40, "Narrator")
                .subtitle("Choose wisely.", 60, 30, "Narrator")
                .sound(100, SoundEvents.NOTE_BLOCK_BELL.value(), 1f, 1.5f)
                .subtitle("...or don't. It rarely matters.", 110, 40, "Narrator")
                .build();
        playAndRegister(player, definition, Map.of(), List.of());
    }

    // --- 5. full ActorTrack: visibility (step interpolation), position, rotation, on a real entity ---

    private static void playActor(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 playerPos = player.position();
        Vec3 forward = player.getLookAngle();
        Vec3 side = new Vec3(forward.z, 0, -forward.x);
        float yaw = player.getYRot();
        Vec3 npcPos = playerPos.add(forward.scale(5));
        Vec3 cameraPos = playerPos.add(side.scale(3)).add(0, 2.0, 0);
        float cameraYaw = yawTowards(cameraPos, npcPos);

        Villager phantom = spawnHiddenVillager(level, npcPos, "Phantom");
        if (phantom == null) {
            EngineLog.channel("Cinematic").error("Failed to spawn the demo NPC").toChat(player);
            return;
        }

        Vec3 walkTo = npcPos.add(forward.scale(2));
        CutsceneDefinition definition = CutsceneDefinition.define(id("example_actor"))
                .duration(140)
                .shot(0, 140, camera -> camera
                        .position(0, pos(cameraPos.x, cameraPos.y, cameraPos.z))
                        .rotation(0, rot(cameraYaw, 10)))
                .actor("phantom", actor -> actor
                        .visible(0, false)
                        .visible(30, true)
                        .position(30, pos(npcPos.x, npcPos.y, npcPos.z))
                        .position(90, pos(walkTo.x, walkTo.y, walkTo.z))
                        .rotation(30, rot(yaw + 180, 0))
                        .rotation(90, rot(yaw, 0))
                        .visible(120, false))
                .subtitle("It was here the whole time.", 35, 40, "???")
                .build();

        playAndRegister(player, definition, Map.of("phantom", phantom.getId()), List.of(phantom));
    }

    // --- 6. pure multi-shot: three hard cuts, no other complexity, to isolate shot-switching ---

    private static void playShots(ServerPlayer player) {
        Vec3 origin = player.position();
        Vec3 forward = player.getLookAngle();
        float yaw = player.getYRot();

        Vec3 shot1 = origin.add(0, 2, 0).subtract(forward.scale(2));
        Vec3 shot2 = origin.add(forward.scale(4)).add(0, 3, 0);
        Vec3 shot3 = origin.subtract(forward.scale(4)).add(0, 1.5, 0);

        CutsceneDefinition definition = CutsceneDefinition.define(id("example_shots"))
                .duration(120)
                .shot(0, 40, camera -> camera
                        .position(0, pos(shot1.x, shot1.y, shot1.z))
                        .rotation(0, rot(yaw, 10)))
                .shot(40, 40, camera -> camera
                        .position(40, pos(shot2.x, shot2.y, shot2.z))
                        .rotation(40, rot(yaw + 90, -20)))
                .shot(80, 40, camera -> camera
                        .position(80, pos(shot3.x, shot3.y, shot3.z))
                        .rotation(80, rot(yaw + 180, 0)))
                .subtitle("Shot 1", 5, 30)
                .subtitle("Shot 2", 45, 30)
                .subtitle("Shot 3", 85, 30)
                .build();
        playAndRegister(player, definition, Map.of(), List.of());
    }

    // --- 7. Trigger -> EventBus, with a real observable reaction (see onTrigger below) ---

    private static void playTrigger(ServerPlayer player) {
        Vec3 forward = player.getLookAngle();
        Vec3 shotPos = establishingShot(player, forward);
        float yaw = player.getYRot();
        CutsceneDefinition definition = CutsceneDefinition.define(id("example_trigger"))
                .duration(100)
                .shot(0, 100, camera -> camera
                        .position(0, pos(shotPos.x, shotPos.y, shotPos.z))
                        .rotation(0, rot(yaw, 8)))
                .subtitle("Something is about to happen...", 10, 40)
                .trigger(60, TRIGGER_FLASH)
                .build();
        playAndRegister(player, definition, Map.of(), List.of());
    }

    // --- 8. the kitchen sink: 3 shots, 2 actors, several subtitles/speakers, audio, FOV, a trigger ---

    private static void playEpic(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 playerPos = player.position();
        Vec3 forward = player.getLookAngle();
        Vec3 side = new Vec3(forward.z, 0, -forward.x);
        float yaw = player.getYRot();

        Vec3 strangerPos = playerPos.add(forward.scale(6));
        Vec3 guardPos = strangerPos.subtract(side.scale(4));

        Vec3 shot1Start = playerPos.subtract(forward.scale(3)).add(0, 2.2, 0);
        Vec3 shot1End = strangerPos.subtract(forward.scale(2)).add(0, 1.7, 0);
        Vec3 shot2Pos = strangerPos.add(forward.scale(2)).add(0, 1.8, 0);
        Vec3 shot3Pos = playerPos.add(0, 6, 0).subtract(forward.scale(6));

        Villager stranger = spawnHiddenVillager(level, strangerPos, "Hooded Stranger");
        Villager guard = spawnHiddenVillager(level, guardPos, "Guard");
        if (stranger == null || guard == null) {
            EngineLog.channel("Cinematic").error("Failed to spawn the demo NPCs").toChat(player);
            return;
        }

        Vec3 guardApproach = strangerPos.add(side.scale(1));
        CutsceneDefinition definition = CutsceneDefinition.define(id("example_epic"))
                .duration(360)
                .shot(0, 140, camera -> camera
                        .position(0, pos(shot1Start.x, shot1Start.y, shot1Start.z), Interpolators.VECTOR3F.eased(Easing.EASE_IN_OUT_CUBIC))
                        .position(80, pos(shot1End.x, shot1End.y, shot1End.z))
                        .rotation(0, rot(yaw, 5))
                        .fov(0, 70f)
                        .fov(60, 35f, Interpolators.FLOAT.eased(Easing.EASE_OUT_QUAD))
                        .fov(140, 55f))
                .shot(140, 100, camera -> camera
                        .position(140, pos(shot2Pos.x, shot2Pos.y, shot2Pos.z))
                        .rotation(140, rot(yaw + 180, 0)))
                .shot(240, 120, camera -> camera
                        .position(240, pos(shot3Pos.x, shot3Pos.y, shot3Pos.z), Interpolators.VECTOR3F.eased(Easing.EASE_IN_OUT_SINE))
                        .position(360, pos(shot3Pos.x, shot3Pos.y + 1, shot3Pos.z))
                        .rotation(240, rot(yaw, 30)))
                .actor("stranger", actor -> actor
                        .visible(0, false)
                        .visible(20, true)
                        .rotation(20, rot(yaw + 180, 0))
                        .rotation(100, rot(yaw, 0))
                        .visible(300, false))
                .actor("guard", actor -> actor
                        .visible(0, false)
                        .visible(150, true)
                        .position(150, pos(guardPos.x, guardPos.y, guardPos.z))
                        .position(220, pos(guardApproach.x, guardApproach.y, guardApproach.z))
                        .rotation(150, rot(yaw + 90, 0))
                        .visible(300, false))
                .sound(20, SoundEvents.VILLAGER_AMBIENT, 1f, 0.8f)
                .subtitle("Hooded Stranger: You shouldn't have come here.", 25, 45, "Hooded Stranger")
                .trigger(60, TRIGGER_EPIC_REVEAL)
                .subtitle("Hooded Stranger: But since you have...", 100, 35, "Hooded Stranger")
                .sound(150, SoundEvents.VILLAGER_AMBIENT, 1f, 1.3f)
                .subtitle("Guard: Sir, we have a visitor.", 155, 40, "Guard")
                .subtitle("Hooded Stranger: I can see that.", 200, 35, "Hooded Stranger")
                .subtitle("...", 260, 30)
                .build();

        playAndRegister(player, definition,
                Map.of("stranger", stranger.getId(), "guard", guard.getId()),
                List.of(stranger, guard));
    }

    // --- 9. Flow -> cutscene -> Flow, run for real (not just compile-checked) ---

    private static void playFlowIntegration(ServerPlayer player) {
        Vec3 forward = player.getLookAngle();
        Vec3 shotPos = establishingShot(player, forward);
        float yaw = player.getYRot();
        CutsceneDefinition definition = CutsceneDefinition.define(id("example_flow_cutscene"))
                .duration(80)
                .shot(0, 80, camera -> camera
                        .position(0, pos(shotPos.x, shotPos.y, shotPos.z))
                        .rotation(0, rot(yaw, 8)))
                .subtitle("A short vision passes...", 10, 50)
                .build();
        CutsceneRegistry.register(definition);

        Flow flow = Flow.sequence(
                Flow.action(ctx -> CinematicManager.play(ctx.player(), definition, Map.of())),
                Flow.waitForEvent(CutsceneCompletedEvent.class,
                        (ctx, event) -> event.cutsceneId().equals(definition.id()) && event.player().equals(ctx.player())),
                Flow.action(ctx -> EngineLog.channel("Cinematic")
                        .success("Flow resumed after the cutscene completed — the story continues.")
                        .toChat(ctx.player())));

        FlowRegistry.register(FLOW_DEMO_FLOW_ID, flow);
        FlowManager.start(FLOW_DEMO_FLOW_ID, player);
        // The Flow's own first action already calls CinematicManager.play synchronously (Flow
        // runs up to its first blocking point within this same call), so registering cleanup here
        // — after FlowManager.start returns — follows the same ordering rule as playAndRegister.
        ACTIVE_EXAMPLES.put(player.getUUID(), new PendingCleanup(definition.id(), List.of()));
    }

    // --- 10. two actors talking to each other: proper shot/reverse-shot dialogue coverage ---

    /**
     * Two villagers face each other and converse while the player just watches — no actor
     * bindings resolve to the player at all here, unlike every earlier example. The camera cuts
     * through the standard four-shot pattern any two-character dialogue scene uses: a wide
     * establishing two-shot, then over-the-shoulder singles on whichever villager is currently
     * speaking (camera near the *listener*, framing the *speaker* — the classic shot/reverse-shot
     * pairing), alternating with the dialogue, and a final wide shot to close the scene. Nothing
     * new in {@code cinematic} was needed for this — it's exactly {@code Shot} (camera cuts) +
     * {@code ActorTrack} (two independent bindings) + {@code SubtitleTrack} (alternating speakers)
     * used together.
     */
    private static void playConversation(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 playerPos = player.position();
        Vec3 forward = player.getLookAngle();
        Vec3 side = new Vec3(forward.z, 0, -forward.x);

        Vec3 center = playerPos.add(forward.scale(9));
        Vec3 villagerAPos = center.subtract(side.scale(1.5));
        Vec3 villagerBPos = center.add(side.scale(1.5));
        float yawAtoB = yawTowards(villagerAPos, villagerBPos);
        float yawBtoA = yawTowards(villagerBPos, villagerAPos);

        Villager villagerA = spawnVillager(level, villagerAPos, yawAtoB, "Elm", false);
        Villager villagerB = spawnVillager(level, villagerBPos, yawBtoA, "Rowan", false);
        if (villagerA == null || villagerB == null) {
            EngineLog.channel("Cinematic").error("Failed to spawn the demo NPCs").toChat(player);
            return;
        }

        // Wide two-shot: pulled back along `forward`, offset to the side for a 3/4 angle rather
        // than a flat profile view.
        Vec3 wideOpen = center.subtract(forward.scale(4)).add(side.scale(3)).add(0, 2.6, 0);
        float wideOpenYaw = yawTowards(wideOpen, center);
        // Over-the-shoulder on A: camera near B, pulled back past B (away from A) so B's shoulder
        // reads at frame edge, aimed at A.
        Vec3 shotOnA = villagerBPos.add(side.scale(0.9)).subtract(forward.scale(0.6)).add(0, 1.6, 0);
        float shotOnAYaw = yawTowards(shotOnA, villagerAPos.add(0, 1.5, 0));
        // Mirror: over-the-shoulder on B, camera near A.
        Vec3 shotOnB = villagerAPos.subtract(side.scale(0.9)).subtract(forward.scale(0.6)).add(0, 1.6, 0);
        float shotOnBYaw = yawTowards(shotOnB, villagerBPos.add(0, 1.5, 0));
        // Wide close: mirrored to the opposite side from the opening wide shot, so the scene
        // doesn't end on a frame identical to the one it started on.
        Vec3 wideClose = center.subtract(forward.scale(4)).subtract(side.scale(3)).add(0, 2.6, 0);
        float wideCloseYaw = yawTowards(wideClose, center);

        CutsceneDefinition definition = CutsceneDefinition.define(id("example_conversation"))
                .duration(440)
                .shot(0, 60, camera -> camera
                        .position(0, pos(wideOpen.x, wideOpen.y, wideOpen.z))
                        .rotation(0, rot(wideOpenYaw, 10)))
                .shot(60, 80, camera -> camera
                        .position(60, pos(shotOnA.x, shotOnA.y, shotOnA.z))
                        .rotation(60, rot(shotOnAYaw, 4)))
                .shot(140, 80, camera -> camera
                        .position(140, pos(shotOnB.x, shotOnB.y, shotOnB.z))
                        .rotation(140, rot(shotOnBYaw, 4)))
                .shot(220, 80, camera -> camera
                        .position(220, pos(shotOnA.x, shotOnA.y, shotOnA.z))
                        .rotation(220, rot(shotOnAYaw, 4)))
                .shot(300, 80, camera -> camera
                        .position(300, pos(shotOnB.x, shotOnB.y, shotOnB.z))
                        .rotation(300, rot(shotOnBYaw, 4)))
                .shot(380, 60, camera -> camera
                        .position(380, pos(wideClose.x, wideClose.y, wideClose.z))
                        .rotation(380, rot(wideCloseYaw, 10)))
                .actor("villagerA", actor -> actor.rotation(0, rot(yawAtoB, 0)))
                .actor("villagerB", actor -> actor.rotation(0, rot(yawBtoA, 0)))
                .sound(5, SoundEvents.VILLAGER_AMBIENT, 1f, 0.9f)
                .subtitle("Two villagers meet near the old well.", 5, 45)
                .subtitle("Have you heard? The old well finally ran dry.", 75, 45, "Elm")
                .subtitle("Aye, I heard. Third one this month.", 155, 45, "Rowan")
                .subtitle("The council should do something about it.", 235, 45, "Elm")
                .subtitle("The council never does anything about it.", 315, 45, "Rowan")
                .build();

        playAndRegister(player, definition,
                Map.of("villagerA", villagerA.getId(), "villagerB", villagerB.getId()),
                List.of(villagerA, villagerB));
    }

    // --- 11. a fast drone-style flythrough: a smooth Catmull-Rom path, weaving low, then a
    //         climbing turn into a wide reveal — pure camera, no actors/subtitles ---

    /**
     * Builds the whole camera move from one {@code CatmullRomSpline<Transform>} through nine
     * hand-placed waypoints (the same combined position+rotation spline {@code CameraPathCommand}
     * already demonstrates for {@code math} on its own), then walks it at *constant speed* via
     * {@link ArcLengthTable} with an eased overall start/stop — exactly {@code
     * CameraPathAnimator}'s own technique, reused rather than reinvented. The spline itself is
     * densely resampled into plain keyframes (still nothing but {@code CameraTrack}/{@code
     * KeyframeTrack} underneath — no change to either), which is what lets a true smooth curve
     * live inside a system whose {@code Track} contract only ever blends between two adjacent
     * keyframes: enough samples close together and the piecewise-linear result reads as one
     * continuous sweep.
     */
    private static void playFlythrough(ServerPlayer player) {
        Vec3 playerPos = player.position();
        Vec3 forward = player.getLookAngle();
        Vec3 side = new Vec3(forward.z, 0, -forward.x);
        float baseYaw = player.getYRot();

        List<Transform> waypoints = new ArrayList<>();
        // Low, weaving pass — "between the houses".
        waypoints.add(flightWaypoint(playerPos.subtract(forward.scale(2)).add(0, 2.0, 0), baseYaw, 8));
        waypoints.add(flightWaypoint(playerPos.add(forward.scale(6)).subtract(side.scale(3)).add(0, 2.3, 0), baseYaw - 12, 10));
        waypoints.add(flightWaypoint(playerPos.add(forward.scale(12)).add(side.scale(3)).add(0, 2.3, 0), baseYaw + 12, 10));
        waypoints.add(flightWaypoint(playerPos.add(forward.scale(18)).subtract(side.scale(3.5)).add(0, 2.5, 0), baseYaw - 12, 10));
        waypoints.add(flightWaypoint(playerPos.add(forward.scale(24)).add(side.scale(3.5)).add(0, 2.5, 0), baseYaw + 12, 10));
        waypoints.add(flightWaypoint(playerPos.add(forward.scale(30)).add(0, 4.0, 0), baseYaw, 12));
        // Climbing 180° turn into a wide overview.
        waypoints.add(flightWaypoint(playerPos.add(forward.scale(34)).add(0, 9, 0), baseYaw + 60, 20));
        waypoints.add(flightWaypoint(playerPos.add(forward.scale(26)).add(0, 16, 0), baseYaw + 130, 35));
        waypoints.add(flightWaypoint(playerPos.add(forward.scale(20)).add(0, 22, 0), baseYaw + 180, 45));

        CatmullRomSpline<Transform> path = CatmullRomSpline.centripetal(
                Interpolators.TRANSFORM,
                (a, b) -> a.translation().distance(b.translation()),
                waypoints);
        Curve<Vector3f> positionCurve = t -> path.sample(t).translation();
        ArcLengthTable arcLength = ArcLengthTable.build(positionCurve, 64);

        int duration = 120;
        int sampleCount = 40;
        CutsceneDefinition definition = CutsceneDefinition.define(id("example_flythrough"))
                .duration(duration)
                .shot(0, duration, camera -> {
                    for (int i = 0; i <= sampleCount; i++) {
                        float progress = (float) i / sampleCount;
                        float eased = Easing.EASE_IN_OUT_CUBIC.apply(progress);
                        float t = arcLength.parameterAtFraction(eased);
                        Transform sample = path.sample(t);
                        int tick = Math.round(progress * duration);
                        camera.position(tick, sample.translation());
                        camera.rotation(tick, sample.rotation());
                    }
                    camera.fov(0, 90f);
                    camera.fov((int) (duration * 0.62f), 90f);
                    camera.fov(duration, 105f, Interpolators.FLOAT.eased(Easing.EASE_OUT_QUAD));
                })
                .build();

        playAndRegister(player, definition, Map.of(), List.of());
    }

    private static Transform flightWaypoint(Vec3 position, float yaw, float pitch) {
        Vector3f translation = new Vector3f((float) position.x, (float) position.y, (float) position.z);
        return Transform.of(translation, Angles.fromYawPitch(yaw, pitch), new Vector3f(1, 1, 1));
    }

    // --- shared helpers ---

    private static Villager spawnHiddenVillager(ServerLevel level, Vec3 pos, String name) {
        return spawnVillager(level, pos, 0f, name, true);
    }

    private static Villager spawnVillager(ServerLevel level, Vec3 pos, float yaw, String name, boolean startHidden) {
        Villager villager = EntityType.VILLAGER.create(level);
        if (villager == null) {
            return null;
        }
        villager.setPos(pos.x, pos.y, pos.z);
        villager.setYRot(yaw);
        villager.setYHeadRot(yaw);
        villager.setInvisible(startHidden);
        // These are real, server-simulated entities — without this, their own AI (wandering,
        // looking at the nearest player, trying to path to a workstation) keeps running the whole
        // time underneath whatever ActorTrack is animating, fighting it every tick. setNoAi(true)
        // disables the entire goal selector before it ever runs a single goal, so the only thing
        // that ever moves these villagers is the cutscene itself.
        villager.setNoAi(true);
        villager.setCustomName(Component.literal(name));
        villager.setCustomNameVisible(true);
        villager.setPersistenceRequired();
        level.addFreshEntity(villager);
        return villager;
    }

    private static void playAndRegister(ServerPlayer player, CutsceneDefinition definition,
                                         Map<String, Integer> actorBindings, List<Entity> spawned) {
        CutsceneRegistry.register(definition);
        CinematicManager.play(player, definition, actorBindings);
        ACTIVE_EXAMPLES.put(player.getUUID(), new PendingCleanup(definition.id(), spawned));
    }

    @com.dimalab.storymodengine.api.event.SubscribeEvent
    public static void onExampleFinished(CutsceneCompletedEvent event) {
        cleanup(event.player().getUUID(), event.cutsceneId());
    }

    @com.dimalab.storymodengine.api.event.SubscribeEvent
    public static void onExampleFinished(CutsceneCancelledEvent event) {
        cleanup(event.player().getUUID(), event.cutsceneId());
    }

    private static void cleanup(UUID playerId, ResourceLocation cutsceneId) {
        PendingCleanup pending = ACTIVE_EXAMPLES.get(playerId);
        if (pending == null || !pending.cutsceneId().equals(cutsceneId)) {
            return;
        }
        ACTIVE_EXAMPLES.remove(playerId);
        for (Entity entity : pending.spawned()) {
            if (entity.isAlive()) {
                entity.discard();
            }
        }
    }

    /** A real, visible gameplay reaction to each example's {@code Trigger} — proves Trigger → EventBus end to end, not just compiled. */
    @com.dimalab.storymodengine.api.event.SubscribeEvent
    public static void onTrigger(CutsceneTriggerEvent event) {
        if (!(event.player() instanceof ServerPlayer player)) {
            return;
        }
        if (TRIGGER_FLASH.equals(event.triggerId())) {
            player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20, 0));
            EngineLog.channel("Cinematic").success("Trigger fired: a brief glow.").toChat(player);
        } else if (TRIGGER_EPIC_REVEAL.equals(event.triggerId())) {
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 40, 0));
            EngineLog.channel("Cinematic").success("Trigger fired: the truth becomes clear.").toChat(player);
        }
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(StoryModEngine.MODID, path);
    }

    private record PendingCleanup(ResourceLocation cutsceneId, List<Entity> spawned) {
    }
}
