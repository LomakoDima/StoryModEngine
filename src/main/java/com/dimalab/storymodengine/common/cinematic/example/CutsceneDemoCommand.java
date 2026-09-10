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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.dimalab.storymodengine.common.cinematic.CutsceneDefinition.pos;
import static com.dimalab.storymodengine.common.cinematic.CutsceneDefinition.rot;

/**
 * {@code /sme cutscene demo} — the required live demonstration. Spawns a temporary
 * villager a few blocks ahead of the player, builds a two-shot, ten-second {@link
 * CutsceneDefinition} relative to the player's current position/facing (see {@link
 * CutsceneExamples}'s Javadoc for why this is built fresh here rather than as a static field), and
 * starts it through {@link CinematicManager} exactly like any other mod would.
 *
 * <pre>
 * 0.0s  camera starts behind the player
 * 2.0s  camera has moved close to the NPC
 * 2.5–3.25s  NPC turns to face the player
 * 3.5s  subtitle: "You're finally here."
 * 5.0s  second shot begins (reverse angle, beyond the NPC looking back)
 * 7.0s  subtitle: "We need to talk."
 * 10.0s cutscene completes, player regains control
 * </pre>
 *
 * <p>The spawned "Traveler" villager is temporary and demo-owned, not part of {@code cinematic}
 * itself: {@link #onCutsceneFinished} discards it as soon as *this* cutscene (matched by {@link
 * #DEMO_ID}) completes or is cancelled for the player it was spawned for, so repeated runs don't
 * leave villagers behind. Registered per-player in {@link #SPAWNED_NPCS} *after* {@code
 * CinematicManager.play} returns — deliberately after, not before: if a previous demo run is still
 * active for that player, {@code play} cancels it first (posting {@link CutsceneCancelledEvent}),
 * and that cleanup needs to see the *old* villager still in the map, not one already overwritten
 * by this new run.
 */
@Mod.EventBusSubscriber(modid = StoryModEngine.MODID)
public final class CutsceneDemoCommand {

    private static final ResourceLocation DEMO_ID = new ResourceLocation(StoryModEngine.MODID, "demo");
    private static final Map<UUID, Villager> SPAWNED_NPCS = new ConcurrentHashMap<>();

    private CutsceneDemoCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("sme")
                .then(Commands.literal("cutscene")
                        .then(Commands.literal("demo").executes(CutsceneDemoCommand::demo))));
    }

    @com.dimalab.storymodengine.api.event.SubscribeEvent
    public static void onCutsceneFinished(CutsceneCompletedEvent event) {
        discardNpc(event.player().getUUID(), event.cutsceneId());
    }

    @com.dimalab.storymodengine.api.event.SubscribeEvent
    public static void onCutsceneFinished(CutsceneCancelledEvent event) {
        discardNpc(event.player().getUUID(), event.cutsceneId());
    }

    private static void discardNpc(UUID playerId, ResourceLocation cutsceneId) {
        if (!DEMO_ID.equals(cutsceneId)) {
            return;
        }
        Villager villager = SPAWNED_NPCS.remove(playerId);
        if (villager != null && villager.isAlive()) {
            villager.discard();
        }
    }

    private static int demo(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();

        Vec3 playerPos = player.position();
        Vec3 forward = player.getLookAngle();
        float playerYaw = player.getYRot();

        Vec3 npcPos = playerPos.add(forward.scale(6));
        Vec3 behindPlayer = playerPos.subtract(forward.scale(3)).add(0, 2.2, 0);
        Vec3 nearNpc = npcPos.subtract(forward.scale(2)).add(0, 1.7, 0);
        Vec3 secondShotPos = npcPos.add(forward.scale(2.5)).add(0, 1.8, 0);

        Villager villager = EntityType.VILLAGER.create(player.serverLevel());
        if (villager == null) {
            EngineLog.channel("Cinematic").error("Failed to spawn the demo NPC").toChat(player);
            return 0;
        }
        villager.setPos(npcPos.x, npcPos.y, npcPos.z);
        villager.setYRot(playerYaw);
        villager.setYHeadRot(playerYaw);
        // Without this, the villager's own AI (wandering, looking at the player, pathing toward a
        // workstation) keeps running server-side the whole time, fighting whatever the cutscene
        // itself animates. setNoAi(true) disables the goal selector before it ever runs a goal.
        villager.setNoAi(true);
        villager.setCustomName(Component.literal("Traveler"));
        villager.setCustomNameVisible(true);
        villager.setPersistenceRequired();
        player.serverLevel().addFreshEntity(villager);

        CutsceneDefinition definition = CutsceneDefinition.define(DEMO_ID)
                .duration(200)
                .shot(0, 100, camera -> camera
                        .position(0, pos(behindPlayer.x, behindPlayer.y, behindPlayer.z))
                        .position(40, pos(nearNpc.x, nearNpc.y, nearNpc.z))
                        .rotation(0, rot(playerYaw, 5))
                        .fov(40, 60f))
                .shot(100, 100, camera -> camera
                        .position(100, pos(secondShotPos.x, secondShotPos.y, secondShotPos.z))
                        .rotation(100, rot(playerYaw + 180, 0)))
                .actor("npc", actor -> actor
                        .rotation(50, rot(playerYaw, 0))
                        .rotation(65, rot(playerYaw + 180, 0)))
                .sound(60, SoundEvents.VILLAGER_AMBIENT, 1.0f, 1.0f)
                .subtitle("You're finally here.", 70, 30)
                .subtitle("We need to talk.", 140, 40)
                .build();

        CutsceneRegistry.register(definition);
        CinematicManager.play(player, definition, Map.of("npc", villager.getId()));
        SPAWNED_NPCS.put(player.getUUID(), villager);
        EngineLog.channel("Cinematic").success("Cutscene demo started.").toChat(player);
        return 1;
    }
}
