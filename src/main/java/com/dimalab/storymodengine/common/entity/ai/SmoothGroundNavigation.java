package com.dimalab.storymodengine.common.entity.ai;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.phys.Vec3;

/**
 * A ground navigation that walks <b>straight</b> at what it is heading for whenever the way is clear,
 * instead of visiting every block-grid node of its path.
 *
 * <p>A path is a chain of nodes snapped to the block grid, so following it literally produces
 * staircase movement — the mob steps to a node, turns, steps to the next, turns back. Vanilla has a
 * hook for exactly this, {@code PathNavigation.canMoveDirectly}, but in 1.20.1 the base implementation
 * <b>always returns false</b> and {@code GroundPathNavigation} never overrides it (verified against
 * source), so ground mobs never take the shortcut and the staircase is what you see.
 *
 * <p>Two things happen here. {@link #canMoveDirectly} gets a real implementation, which is enough for
 * vanilla's own node-skipping heuristic to start working; and {@link #tick} looks a few nodes further
 * ahead and steers straight at the farthest one it can reach — the standard "string pulling" that
 * turns a grid path back into the line a person would actually walk.
 *
 * <p>This never changes <i>where</i> the mob is going: the path is still vanilla's, and any node it
 * cannot reach in a straight line is still walked to normally.
 */
public class SmoothGroundNavigation extends GroundPathNavigation {

    /** How many nodes ahead a shortcut may reach. Larger cuts more corners but costs more collision checks per tick. */
    private static final int MAX_LOOKAHEAD_NODES = 4;
    private static final double HEIGHT_EPSILON = 1.0e-3;
    /** Bounds how far down a shortcut may drop — a heuristic ceiling for "still a safe walking shortcut," not a fall-damage calculation. */
    private static final double MAX_SAFE_DROP = 3.0;

    public SmoothGroundNavigation(Mob mob, Level level) {
        super(mob, level);
    }

    /**
     * Identical to {@code GroundPathNavigation}'s own (see {@code createPathFinder} there) except for
     * the returned type — the only way to make the A* search itself use {@link NpcPathFinder}'s jump
     * malus, since {@code PathFinder} has no setter for it after construction.
     */
    @Override
    protected PathFinder createPathFinder(int maxVisitedNodes) {
        this.nodeEvaluator = new NpcNodeEvaluator();
        this.nodeEvaluator.setCanPassDoors(true);
        return new NpcPathFinder(this.nodeEvaluator, maxVisitedNodes);
    }

    @Override
    public void tick() {
        super.tick();
        if (isDone() || !this.mob.onGround()) {
            return;
        }
        Path current = getPath();
        if (current == null || current.isDone()) {
            return;
        }
        steerPastNodes(current);
    }

    /** Aim at the farthest node within reach that a straight walk actually gets to, falling back to normal following when none does. */
    private void steerPastNodes(Path path) {
        Vec3 from = this.mob.position();
        int first = path.getNextNodeIndex();
        int last = Math.min(first + MAX_LOOKAHEAD_NODES, path.getNodeCount() - 1);

        for (int index = last; index > first; index--) {
            if (!everyNodeCuttable(path, first, index)) {
                continue;
            }
            Vec3 target = path.getEntityPosAtNode(this.mob, index);
            if (!canMoveDirectly(from, target)) {
                continue;
            }
            this.mob.getMoveControl().setWantedPosition(target.x, target.y, target.z, this.speedModifier);
            return;
        }

        // last <= first here means fewer than two nodes remain ahead — the loop above can never
        // run in that range (it only ever compares first against something strictly greater), so
        // the final leg of every approach silently skipped this class's own straight-line check
        // and fell back to vanilla's raw single-node follow every time, however skewed that one
        // remaining node's grid position was relative to where the mob is actually standing.
        if (last <= first && canCutCorner(path.getNode(first).type)) {
            Vec3 target = path.getEntityPosAtNode(this.mob, first);
            if (canMoveDirectly(from, target)) {
                this.mob.getMoveControl().setWantedPosition(target.x, target.y, target.z, this.speedModifier);
            }
        }
    }

    /** A shortcut may only skip nodes that were safe to cut in the first place — a door or a fire node has to be walked to properly. */
    private boolean everyNodeCuttable(Path path, int fromIndex, int toIndex) {
        for (int index = fromIndex; index <= toIndex; index++) {
            if (!canCutCorner(path.getNode(index).type)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether the mob can reach {@code from} to {@code to} in a straight line — the flat case (no
     * step up or down beyond what it can manage, nothing solid in the way, ground under it the whole
     * time) plus two the flat case used to reject outright: a small jump and a small controlled drop,
     * each checked by {@link NpcNavigationGeometry} against the mob's real collision box rather than
     * just capping the vertical delta. Anything bigger than a safe drop, or a rise past jump range,
     * still falls back to normal node-by-node following exactly as before.
     */
    @Override
    protected boolean canMoveDirectly(Vec3 from, Vec3 to) {
        if (!this.mob.onGround()) {
            return false;
        }
        double dy = to.y - from.y;
        double step = this.mob.getStepHeight() + HEIGHT_EPSILON;
        if (Math.abs(dy) <= step) {
            return NpcNavigationGeometry.canWalkDirectly(this.mob, this.level, from, to);
        }
        if (dy > 0) {
            return NpcNavigationGeometry.canJumpTo(this.mob, this.level, from, to);
        }
        return NpcNavigationGeometry.canDropTo(this.mob, this.level, from, to, MAX_SAFE_DROP);
    }
}
