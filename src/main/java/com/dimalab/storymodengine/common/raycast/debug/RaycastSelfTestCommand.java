package com.dimalab.storymodengine.common.raycast.debug;

import com.dimalab.storymodengine.common.StoryModEngine;
import com.dimalab.storymodengine.common.logging.EngineLog;
import com.dimalab.storymodengine.common.raycast.Raycast;
import com.dimalab.storymodengine.common.raycast.RaycastResult;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /storymodengine raycast selftest} — this project has no JUnit setup (verified: no {@code
 * src/test}, no test dependency in {@code build.gradle}), so this is the same in-game,
 * assert-and-report substitute {@code quest.debug.QuestCompilerTestCommand} already established.
 * Nearly everything in {@code raycast} needs a real {@code Level} (block/entity clipping, {@code
 * Level#getEntities}), so unlike {@code QuestCompilerTestCommand} there's no pure-JVM half to split
 * out — every check here runs against the invoking player's real, live level.
 *
 * <p>All test rays are built along a fixed direction ({@code +X}, "east") from a point above the
 * player, not the player's actual look vector — deterministic and independent of which way the
 * tester happens to be facing. Temporary {@link ArmorStand}s (invisible, no gravity) stand in for
 * "an entity to hit"; every one spawned here is discarded in a {@code finally} block regardless of
 * pass/fail, so this command never leaves world state behind. The block-hit test relies on there
 * being solid ground somewhere below the player within 300 blocks (true for any normal world/void
 * only near the world's built limit) — its failure message says so explicitly if that assumption
 * doesn't hold in a given test run.
 */
@Mod.EventBusSubscriber(modid = StoryModEngine.MODID)
public final class RaycastSelfTestCommand {

    private static final Vec3 EAST = new Vec3(1, 0, 0);

    private RaycastSelfTestCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("storymodengine")
                .then(Commands.literal("raycast")
                        .then(Commands.literal("selftest").executes(RaycastSelfTestCommand::run))));
    }

    private static int run(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        Vec3 origin = player.position().add(0, 1.0, 0);
        List<String> failures = new ArrayList<>();
        List<Entity> spawned = new ArrayList<>();

        try {
            testBlockHit(level, player, failures);
            testMiss(level, origin, failures);
            testEntityHit(level, origin, spawned, failures);
            testEntityExclusion(level, origin, spawned, failures);
            testEntityFiltering(level, origin, spawned, failures);
            testBlockFiltering(level, player, failures);
            testDistanceLimit(level, player, failures);
            testStartEndRay(level, origin, failures);
            testLineOfSight(level, player, spawned, failures);
            testNearestHit(level, origin, spawned, failures);
        } finally {
            for (Entity entity : spawned) {
                entity.discard();
            }
        }

        if (failures.isEmpty()) {
            EngineLog.channel("Raycast").success("selftest: all checks passed").toChat(player);
        } else {
            String joined = String.join("; ", failures);
            EngineLog.channel("Raycast").error("selftest: {} failure(s): {}", failures.size(), joined).toChat(player);
        }
        return failures.isEmpty() ? 1 : 0;
    }

    /** §18.1 — a downward cast should hit solid ground somewhere below the player in any normal world. */
    private static void testBlockHit(ServerLevel level, ServerPlayer player, List<String> failures) {
        RaycastResult result = Raycast.from(level, player.position().add(0, 1.0, 0), new Vec3(0, -1, 0))
                .distance(300).blocks().cast();
        checkTrue(failures, "block hit: cast downward 300 blocks", result.hitBlock(),
                "expected to hit ground below the player — got " + result.type());
    }

    /** §18.3 — a 1cm cast straight up essentially never has a block immediately at eye level. */
    private static void testMiss(ServerLevel level, Vec3 origin, List<String> failures) {
        RaycastResult result = Raycast.from(level, origin, new Vec3(0, 1, 0)).distance(0.01).blocks().cast();
        checkTrue(failures, "miss: 0.01-block cast", result.missed(), "expected a miss — got " + result.type());
    }

    /** §18.2 — a spawned marker entity directly along the ray should be the nearest (and only) entity hit. */
    private static void testEntityHit(ServerLevel level, Vec3 origin, List<Entity> spawned, List<String> failures) {
        ArmorStand target = spawnMarker(level, origin.add(EAST.scale(5)));
        spawned.add(target);
        RaycastResult result = Raycast.from(level, origin, EAST).distance(10).entities().cast();
        checkTrue(failures, "entity hit", result.hitEntity() && result.entity().orElse(null) == target,
                "expected to hit the spawned marker at +5 east — got " + result.type());
    }

    /** §18.4 — excluding the nearer marker should let the cast reach the farther one instead. */
    private static void testEntityExclusion(ServerLevel level, Vec3 origin, List<Entity> spawned, List<String> failures) {
        ArmorStand near = spawnMarker(level, origin.add(EAST.scale(3)));
        ArmorStand far = spawnMarker(level, origin.add(EAST.scale(6)));
        spawned.add(near);
        spawned.add(far);
        RaycastResult result = Raycast.from(level, origin, EAST).distance(10).entities().exclude(near).cast();
        checkTrue(failures, "entity exclusion", result.hitEntity() && result.entity().orElse(null) == far,
                "expected the excluded near marker to be skipped in favor of the far one — got " + result.type());
    }

    /** §18.5 — a type filter should skip a nearer non-matching entity in favor of a farther matching one. */
    private static void testEntityFiltering(ServerLevel level, Vec3 origin, List<Entity> spawned, List<String> failures) {
        ArmorStand nonLiving = spawnMarker(level, origin.add(EAST.scale(3)));
        ArmorStand livingProxy = spawnMarker(level, origin.add(EAST.scale(6)));
        spawned.add(nonLiving);
        spawned.add(livingProxy);
        // ArmorStand IS a LivingEntity, so filter against a type neither one is — proves the filter is
        // actually applied (both should be skipped, ending in a miss) rather than passing vacuously.
        RaycastResult result = Raycast.from(level, origin, EAST).distance(10).entities()
                .filter(Zombie.class).cast();
        checkTrue(failures, "entity filtering (exclude-all case)", result.missed(),
                "expected both markers to be filtered out (neither is a Zombie) — got " + result.type());

        RaycastResult included = Raycast.from(level, origin, EAST).distance(10).entities()
                .filter(LivingEntity.class).cast();
        checkTrue(failures, "entity filtering (include case)", included.hitEntity(),
                "expected an ArmorStand to pass a LivingEntity filter — got " + included.type());
    }

    /** §18.6 — a block filter should reject the real hit and accept a filter matching it. */
    private static void testBlockFiltering(ServerLevel level, ServerPlayer player, List<String> failures) {
        Vec3 start = player.position().add(0, 1.0, 0);
        RaycastResult plain = Raycast.from(level, start, new Vec3(0, -1, 0)).distance(300).blocks().cast();
        if (plain.missed()) {
            failures.add("block filtering: skipped — no ground found below the player to test against");
            return;
        }
        var actualBlock = plain.blockState().orElseThrow().getBlock();

        RaycastResult matching = Raycast.from(level, start, new Vec3(0, -1, 0)).distance(300).blocks()
                .blockFilter(state -> state.is(actualBlock)).cast();
        checkTrue(failures, "block filtering (matching filter)", matching.hitBlock(),
                "expected the matching block filter to still hit — got " + matching.type());

        var mismatch = actualBlock == Blocks.BEDROCK ? Blocks.DIAMOND_BLOCK : Blocks.BEDROCK;
        RaycastResult nonMatching = Raycast.from(level, start, new Vec3(0, -1, 0)).distance(300).blocks()
                .blockFilter(state -> state.is(mismatch)).cast();
        checkTrue(failures, "block filtering (non-matching filter)", nonMatching.missed(),
                "expected a mismatched block filter to report a miss — got " + nonMatching.type());
    }

    /** §18.7 — a distance far short of a known-far target should miss purely from the distance cap. */
    private static void testDistanceLimit(ServerLevel level, ServerPlayer player, List<String> failures) {
        Vec3 start = player.position().add(0, 1.0, 0);
        RaycastResult short_ = Raycast.from(level, start, new Vec3(0, -1, 0)).distance(0.01).blocks().cast();
        checkTrue(failures, "distance limit", short_.missed(),
                "expected a 0.01-block cast to miss regardless of ground below — got " + short_.type());
    }

    /** §18.8 — explicit start/end construction (Raycast.from(level, start).to(end)) computes distance correctly. */
    private static void testStartEndRay(ServerLevel level, Vec3 origin, List<String> failures) {
        Vec3 end = origin.add(EAST.scale(7));
        RaycastResult result = Raycast.from(level, origin).to(end).blocks().cast();
        checkTrue(failures, "start/end ray distance", Math.abs(result.distance() - 7.0) < 0.05,
                "expected .to(end) 7 blocks east to report distance ~7 — got " + result.distance());
    }

    /** §18.9 — a spawned marker with nothing in between should be visible. */
    private static void testLineOfSight(ServerLevel level, ServerPlayer player, List<Entity> spawned, List<String> failures) {
        Vec3 origin = player.position().add(0, 1.0, 0);
        ArmorStand target = spawnMarker(level, origin.add(EAST.scale(4)));
        spawned.add(target);
        boolean visible = Raycast.hasLineOfSight(level, origin, target.position().add(0, target.getEyeHeight(), 0));
        checkTrue(failures, "line of sight (clear)", visible, "expected a clear line of sight to a marker 4 blocks east");
    }

    /** §18.12 — with two markers on the ray, the nearer one must be the one returned, not just any match. */
    private static void testNearestHit(ServerLevel level, Vec3 origin, List<Entity> spawned, List<String> failures) {
        ArmorStand near = spawnMarker(level, origin.add(EAST.scale(2)));
        ArmorStand far = spawnMarker(level, origin.add(EAST.scale(8)));
        spawned.add(near);
        spawned.add(far);
        RaycastResult result = Raycast.from(level, origin, EAST).distance(15).entities().cast();
        checkTrue(failures, "nearest hit", result.hitEntity() && result.entity().orElse(null) == near,
                "expected the nearer marker (+2) to win over the farther one (+8) — got "
                        + result.entity().map(Object::toString).orElse(result.type().toString()));
    }

    private static ArmorStand spawnMarker(ServerLevel level, Vec3 pos) {
        ArmorStand stand = new ArmorStand(level, pos.x, pos.y, pos.z);
        stand.setInvisible(true);
        stand.setNoGravity(true);
        level.addFreshEntity(stand);
        return stand;
    }

    private static void checkTrue(List<String> failures, String label, boolean condition, String detail) {
        if (!condition) {
            failures.add(label + ": " + detail);
        }
    }
}
