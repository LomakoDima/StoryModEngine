package com.dimalab.storymodengine.common.entity.npc;

import com.dimalab.storymodengine.api.entity.NpcAnimationMode;
import com.dimalab.storymodengine.api.entity.NpcBehavior;
import com.dimalab.storymodengine.api.scripting.annotation.StoryCommand;
import com.dimalab.storymodengine.common.entity.ModEntities;
import com.dimalab.storymodengine.common.logging.EngineLog;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * A dev-only performance probe, not a gameplay feature: spawns a grid of {@code STATIONARY} NPCs
 * around a player, each looping a different animation clip, to answer the question a script author
 * actually asked — "what does a town of 50-100+ animated NPCs cost?" — with something they can spawn
 * and watch F3's frame time on, rather than guessing from code alone.
 *
 * <p>Behaviour is fixed at {@link NpcBehavior#STATIONARY} deliberately: this isolates render/skin/pose
 * cost (the thing the render-pipeline optimization pass did <em>not</em> touch — see {@code
 * client.model.gpu.GpuSkinBuffers}' own doc) from AI-goal-tick cost (pathfinding, look-at reacquisition
 * — see {@code NpcLookAtGoal}/{@code SmoothGroundNavigation}), which is a separate, still-open question
 * this tool doesn't answer. A walking/look-at variant would need its own probe.
 *
 * <p>Every clip name here is verified against {@code player_model.gltf}'s own real animation list (60
 * clips) — not guessed — and picked to avoid anything with a likely one-shot/terminal side effect
 * (skipping e.g. a "death" clip).
 */
public final class NpcStressTestCommands {

    private static final String[] CLIP_CYCLE = {
            "idle", "walk", "run", "sneak", "sit", "sleep", "hello", "handsUp",
            "mining", "chooping", "digging", "dance1", "dance2", "dance3", "dance4", "dance5"
    };

    private static final int MAX_COUNT = 500;
    private static final double SPACING = 2.5;
    static final String MODEL_NAME = "player_model";

    /** Tracks every NPC this tool has spawned (across all levels/spawns) so {@code clear} can find them again without a marker tag or a script-declared name. */
    private static final Set<UUID> SPAWNED = new HashSet<>();

    private NpcStressTestCommands() {
    }

    @StoryCommand("npc_spawn_stress_test")
    public static void npcSpawnStressTest(ServerPlayer player, double count) {
        int spawned = spawnGrid(player, (int) Math.round(count));
        EngineLog.channel("Npc").info("npc_spawn_stress_test: spawned {} NPC(s) around {}.", spawned, player.getGameProfile().getName());
    }

    @StoryCommand("npc_clear_stress_test")
    public static void npcClearStressTest(ServerPlayer player) {
        int removed = clearAll(player.serverLevel());
        EngineLog.channel("Npc").info("npc_clear_stress_test: removed {} NPC(s).", removed);
    }

    /**
     * Spawns {@code count} (clamped to {@code [1, MAX_COUNT]}) {@code STATIONARY} NPCs in a square grid
     * centred on {@code player}, each set to loop a different clip from {@link #CLIP_CYCLE} (cycling
     * once every 16). Returns however many actually spawned (never fails outright — a missing model
     * would abort every spawn identically, so this only returns 0 in that one case).
     */
    static int spawnGrid(ServerPlayer player, int count) {
        int clamped = Math.max(1, Math.min(MAX_COUNT, count));
        ServerLevel level = player.serverLevel();
        Vec3 center = player.position();
        float facing = player.getYRot() + 180f;

        int cols = (int) Math.ceil(Math.sqrt(clamped));
        int rows = (int) Math.ceil((double) clamped / cols);

        int spawned = 0;
        for (int i = 0; i < clamped; i++) {
            int row = i / cols;
            int col = i % cols;
            double offsetX = (col - (cols - 1) / 2.0) * SPACING;
            double offsetZ = (row - (rows - 1) / 2.0) * SPACING;

            NpcEntity npc = ModEntities.NPC.get().create(level);
            if (npc == null) {
                EngineLog.channel("Npc").error("npc_spawn_stress_test: could not create the entity — aborting after {}.", spawned);
                break;
            }
            npc.moveTo(center.x + offsetX, center.y, center.z + offsetZ, facing, 0f);
            npc.setModelName(MODEL_NAME);
            npc.setBehavior(NpcBehavior.STATIONARY);
            npc.setCustomName(Component.literal("StressNPC_" + i));
            npc.setCustomNameVisible(false);
            npc.setAnimationMode(NpcAnimationMode.LOOP);
            npc.setAnimation(CLIP_CYCLE[i % CLIP_CYCLE.length]);
            level.addFreshEntity(npc);
            SPAWNED.add(npc.getUUID());
            spawned++;
        }
        return spawned;
    }

    /** Discards every tracked NPC that's still alive in {@code level} (a dead/unloaded entry is just dropped, not an error) and forgets them all. */
    static int clearAll(ServerLevel level) {
        int removed = 0;
        for (UUID id : SPAWNED) {
            Entity entity = level.getEntity(id);
            if (entity != null) {
                entity.discard();
                removed++;
            }
        }
        SPAWNED.clear();
        return removed;
    }

    static String describeClips() {
        return String.format(Locale.ROOT, "%d distinct clips cycling: %s", CLIP_CYCLE.length, String.join(", ", CLIP_CYCLE));
    }
}
