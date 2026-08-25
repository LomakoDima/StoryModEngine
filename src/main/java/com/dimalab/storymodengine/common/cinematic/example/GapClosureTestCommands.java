package com.dimalab.storymodengine.common.cinematic.example;

import com.dimalab.storymodengine.common.StoryModEngine;
import com.dimalab.storymodengine.common.cinematic.CinematicManager;
import com.dimalab.storymodengine.common.cinematic.CutsceneCancelledEvent;
import com.dimalab.storymodengine.common.cinematic.CutsceneCompletedEvent;
import com.dimalab.storymodengine.common.cinematic.CutsceneDefinition;
import com.dimalab.storymodengine.common.cinematic.registry.CutsceneRegistry;
import com.dimalab.storymodengine.common.logging.EngineLog;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.dimalab.storymodengine.common.cinematic.CutsceneDefinition.pos;
import static com.dimalab.storymodengine.common.cinematic.CutsceneDefinition.rot;

/**
 * {@code /storymodengine cinematic test json|authoritative|sequence} — throwaway, hands-on
 * verification for the three "gap closure" features that can't be exercised through any existing
 * command: JSON-loaded definitions, server-authoritative actor movement, and {@code
 * CinematicManager#playSequence}. Not a permanent showcase like {@link CinematicShowcaseCommand} —
 * kept separate and clearly named so it's easy to remove later without touching anything else.
 */
@Mod.EventBusSubscriber(modid = StoryModEngine.MODID)
public final class GapClosureTestCommands {

    private static final ResourceLocation JSON_TEST_ID = new ResourceLocation(StoryModEngine.MODID, "gap_test");
    private static final ResourceLocation AUTHORITATIVE_TEST_ID = new ResourceLocation(StoryModEngine.MODID, "gap_test_authoritative");
    private static final Map<UUID, Villager> SPAWNED_NPCS = new ConcurrentHashMap<>();

    private GapClosureTestCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("storymodengine")
                .then(Commands.literal("cinematic")
                        .then(Commands.literal("test")
                                .then(Commands.literal("json").executes(GapClosureTestCommands::testJson))
                                .then(Commands.literal("authoritative").executes(GapClosureTestCommands::testAuthoritative))
                                .then(Commands.literal("sequence").executes(GapClosureTestCommands::testSequence)))));
    }

    @com.dimalab.storymodengine.api.event.SubscribeEvent
    public static void onFinished(CutsceneCompletedEvent event) {
        discardNpc(event.player().getUUID(), event.cutsceneId());
    }

    @com.dimalab.storymodengine.api.event.SubscribeEvent
    public static void onCancelled(CutsceneCancelledEvent event) {
        discardNpc(event.player().getUUID(), event.cutsceneId());
    }

    private static void discardNpc(UUID playerId, ResourceLocation cutsceneId) {
        if (!AUTHORITATIVE_TEST_ID.equals(cutsceneId)) {
            return;
        }
        Villager villager = SPAWNED_NPCS.remove(playerId);
        if (villager != null && villager.isAlive()) {
            villager.discard();
        }
    }

    /**
     * Proves {@code cinematic.json.CutsceneJsonLoader} actually loaded {@code
     * data/storymodengine/storymodengine/cutscenes/gap_test.json} — teleports the player to a fixed
     * spot high in the sky first (the JSON's own camera path is authored in absolute world
     * coordinates around {@code (0, 200, 0)}, since a static JSON file has no notion of "relative to
     * wherever the player happens to stand" the way code-built examples do) so the test is
     * deterministic regardless of world seed, and so {@code CameraCollision} has real open air to
     * work with rather than whatever terrain happens to sit under the player's actual position.
     */
    private static int testJson(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        CutsceneDefinition definition = CutsceneRegistry.get(JSON_TEST_ID);
        if (definition == null) {
            EngineLog.channel("Cinematic").error(
                    "'{}' isn't registered — the JSON file may have failed to parse (check the log) or a /reload hasn't run yet", JSON_TEST_ID).toChat(player);
            return 0;
        }
        player.teleportTo(player.serverLevel(), 0.5, 205, 0.5, player.getYRot(), player.getXRot());
        CinematicManager.play(player, definition, Map.of());
        EngineLog.channel("Cinematic").success("Playing JSON-loaded cutscene '{}'.", JSON_TEST_ID).toChat(player);
        return 1;
    }

    /**
     * Spawns a villager and moves it via an {@code authoritative()} {@code ActorTrack} — since
     * {@code ClientCutscenePlayer} deliberately skips its own local visual override for an
     * authoritative track (see its Javadoc), the villager only appears to move at all if
     * {@code CinematicManager} is genuinely moving the real, server-side entity each tick; a villager
     * that stays frozen in place would mean the mechanism isn't working, not that it's merely
     * cosmetic.
     */
    private static int testAuthoritative(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        Vec3 playerPos = player.position();
        Vec3 forward = player.getLookAngle();
        Vec3 side = new Vec3(forward.z, 0, -forward.x);

        Vec3 startPos = playerPos.add(forward.scale(6)).subtract(side.scale(3));
        Vec3 endPos = playerPos.add(forward.scale(6)).add(side.scale(3));

        Villager villager = EntityType.VILLAGER.create(level);
        if (villager == null) {
            EngineLog.channel("Cinematic").error("Failed to spawn the test NPC").toChat(player);
            return 0;
        }
        villager.setPos(startPos.x, startPos.y, startPos.z);
        villager.setNoAi(true);
        villager.setCustomName(Component.literal("Authoritative Test"));
        villager.setCustomNameVisible(true);
        villager.setPersistenceRequired();
        level.addFreshEntity(villager);

        float yawStartToEnd = (float) Math.toDegrees(Math.atan2(startPos.x - endPos.x, endPos.z - startPos.z));

        CutsceneDefinition definition = CutsceneDefinition.define(AUTHORITATIVE_TEST_ID)
                .duration(80)
                .shot(0, 80, camera -> camera
                        .position(0, pos(playerPos.x, playerPos.y + 1.7, playerPos.z))
                        .rotation(0, rot(player.getYRot(), 0)))
                .actor("npc", actor -> actor
                        .authoritative()
                        .position(0, pos(startPos.x, startPos.y, startPos.z))
                        .position(80, pos(endPos.x, endPos.y, endPos.z))
                        .rotation(0, rot(yawStartToEnd, 0)))
                .subtitle("Watch the NPC — it's really walking, not just a client-side illusion.", 0, 80)
                .build();

        CutsceneRegistry.register(definition);
        CinematicManager.play(player, definition, Map.of("npc", villager.getId()));
        SPAWNED_NPCS.put(player.getUUID(), villager);
        EngineLog.channel("Cinematic").success("Playing authoritative-actor test — watch the villager, not the camera.").toChat(player);
        return 1;
    }

    /** Proves {@code CinematicManager#playSequence} — two short cutscenes back to back, no manual Flow wiring at the call site. */
    private static int testSequence(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Vec3 playerPos = player.position();
        Vec3 forward = player.getLookAngle();
        float yaw = player.getYRot();

        Vec3 shotA = playerPos.subtract(forward.scale(3)).add(0, 2.2, 0);
        Vec3 shotB = playerPos.add(forward.scale(3)).add(0, 2.2, 0);

        CutsceneDefinition first = CutsceneDefinition.define(new ResourceLocation(StoryModEngine.MODID, "gap_test_sequence_a"))
                .duration(60)
                .shot(0, 60, camera -> camera
                        .position(0, pos(shotA.x, shotA.y, shotA.z))
                        .rotation(0, rot(yaw, 5)))
                .subtitle("First cutscene in the sequence.", 5, 50)
                .build();
        CutsceneDefinition second = CutsceneDefinition.define(new ResourceLocation(StoryModEngine.MODID, "gap_test_sequence_b"))
                .duration(60)
                .shot(0, 60, camera -> camera
                        .position(0, pos(shotB.x, shotB.y, shotB.z))
                        .rotation(0, rot(yaw + 180, 5)))
                .subtitle("Second cutscene — started automatically after the first completed.", 5, 50)
                .build();

        CutsceneRegistry.register(first);
        CutsceneRegistry.register(second);
        CinematicManager.playSequence(player, List.of(first, second));
        EngineLog.channel("Cinematic").success("Playing a 2-cutscene sequence via CinematicManager.playSequence.").toChat(player);
        return 1;
    }
}
