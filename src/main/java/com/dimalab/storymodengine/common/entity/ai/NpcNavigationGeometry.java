package com.dimalab.storymodengine.common.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Sampled-AABB-sweep feasibility checks shared between {@link SmoothGroundNavigation} (checking a
 * live {@code Level} on-tick) and {@link NpcNodeEvaluator} (checking a {@code PathNavigationRegion}
 * snapshot during {@code findPath}) — both are a {@link CollisionGetter} (confirmed: {@code Level}
 * via {@code LevelReader}, {@code PathNavigationRegion} directly), so one implementation serves both.
 * Every method here is a heuristic feasibility check for a shortcut the pathfinder or the
 * string-pulling steerer is considering, not a physics simulation: they answer "is there room and
 * footing," not "exactly how the mob would move through it."
 */
final class NpcNavigationGeometry {

    private static final double HEIGHT_EPSILON = 1.0e-3;
    /** Matches vanilla's own jump-height concept — see {@code WalkNodeEvaluator.getMobJumpHeight()}. */
    private static final double MIN_JUMP_HEIGHT = 1.125;
    /** A single grid-cell diagonal is a short, bounded transition — a fixed small sample count is enough. */
    private static final int DIAGONAL_SAMPLES = 3;

    private NpcNavigationGeometry() {
    }

    /** Same-level straight walk: body clearance the whole way, footing under the walk line throughout — the original {@code SmoothGroundNavigation.canMoveDirectly} body, unchanged. */
    static boolean canWalkDirectly(Mob mob, CollisionGetter level, Vec3 from, Vec3 to) {
        double step = mob.getStepHeight() + HEIGHT_EPSILON;
        return sweep(mob, level, from, to, from.y, step, true);
    }

    /** A rise beyond a walkable step but within jump range: body clearance along a linear approach, footing only required at landing (a jump's arc has no continuous footing by definition). */
    static boolean canJumpTo(Mob mob, CollisionGetter level, Vec3 from, Vec3 to) {
        double step = mob.getStepHeight() + HEIGHT_EPSILON;
        double rise = to.y - from.y;
        double jumpHeight = Math.max(MIN_JUMP_HEIGHT, mob.getStepHeight());
        if (rise <= step || rise > jumpHeight) {
            return false;
        }
        return sweep(mob, level, from, to, to.y, step, false);
    }

    /** A controlled drop bounded by {@code maxSafeDrop}: body clearance along the way, footing only required at landing. */
    static boolean canDropTo(Mob mob, CollisionGetter level, Vec3 from, Vec3 to, double maxSafeDrop) {
        double step = mob.getStepHeight() + HEIGHT_EPSILON;
        double drop = from.y - to.y;
        if (drop <= step || drop > maxSafeDrop) {
            return false;
        }
        return sweep(mob, level, from, to, to.y, step, false);
    }

    private static boolean sweep(Mob mob, CollisionGetter level, Vec3 from, Vec3 to, double footingY, double step, boolean footingEveryStep) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal < 1.0e-4) {
            return footingEveryStep || hasFooting(level, to.x, footingY, to.z, step);
        }

        int samples = Math.max(1, Mth.ceil(horizontal / Math.max(0.25, mob.getBbWidth() * 0.5)));
        AABB box = mob.getBoundingBox();

        for (int i = 1; i <= samples; i++) {
            double t = (double) i / samples;
            double x = from.x + dx * t;
            double z = from.z + dz * t;
            AABB swept = box.move(x - mob.getX(), step, z - mob.getZ());
            if (!level.noCollision(mob, swept)) {
                return false;
            }
            if (footingEveryStep && !hasFooting(level, x, footingY, z, step)) {
                return false;
            }
        }
        return footingEveryStep || hasFooting(level, to.x, footingY, to.z, step);
    }

    private static boolean hasFooting(CollisionGetter level, double x, double y, double z, double step) {
        BlockPos below = BlockPos.containing(x, y - HEIGHT_EPSILON, z).below();
        if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
            return true;
        }
        BlockPos atFeet = BlockPos.containing(x, y + step, z);
        return !level.getBlockState(atFeet).getCollisionShape(level, atFeet).isEmpty();
    }

    /**
     * A single grid-cell diagonal transition vanilla's own {@code isDiagonalValid} would refuse:
     * body clearance only, no continuous footing required mid-transit — matching how a corner is
     * actually cut at speed rather than how a straight walk needs solid ground the whole way.
     */
    static boolean canSqueezeDiagonally(Mob mob, CollisionGetter level, BlockPos node, BlockPos diagonal) {
        double fromX = node.getX() + 0.5;
        double fromZ = node.getZ() + 0.5;
        double toX = diagonal.getX() + 0.5;
        double toZ = diagonal.getZ() + 0.5;
        double y = Math.max(node.getY(), diagonal.getY());
        double half = mob.getBbWidth() / 2.0;
        double height = mob.getBbHeight();

        for (int i = 1; i <= DIAGONAL_SAMPLES; i++) {
            double t = (double) i / DIAGONAL_SAMPLES;
            double x = Mth.lerp(t, fromX, toX);
            double z = Mth.lerp(t, fromZ, toZ);
            AABB box = new AABB(x - half, y, z - half, x + half, y + height, z + half);
            if (!level.noCollision(mob, box)) {
                return false;
            }
        }
        return hasFooting(level, toX, diagonal.getY(), toZ, 0.0);
    }
}
