package com.dimalab.storymodengine.common.entity.ai;

import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.PathFinder;

/**
 * A {@link PathFinder} that adds a small extra cost to any edge that steps up a block, on top of
 * vanilla's own {@link Node#distanceTo}. Architecture idea studied from HollowEngine's own
 * {@code additionalTravelCost} (not their source — this is an independent implementation): vanilla's
 * A* treats a forced jump and a flat step as equally "far" once {@code findAcceptedNode} has decided
 * the jump is reachable at all (verified in {@code WalkNodeEvaluator.java}), so among two routes of
 * similar length it has no reason to prefer the one that stays on the ground. This malus breaks that
 * tie toward the flatter route without forbidding the jump outright — {@link #distance} only feeds
 * {@code PathFinder}'s existing A* cost sum (see {@code findPath}'s {@code node.g + f + node1.costMalus}),
 * it never rejects a neighbor the evaluator already accepted.
 *
 * <p>{@code y} increasing from one node to the next is the reachable proxy for "this step required a
 * jump": {@code WalkNodeEvaluator.findAcceptedNode} only ever returns a higher node when the same-level
 * neighbor was blocked and the mob's step-up budget covered the difference, so every upward edge in an
 * accepted path is, by construction, a step the mob could not simply walk into.
 */
public class NpcPathFinder extends PathFinder {

    private static final float JUMP_COST_MALUS_PER_BLOCK = 0.5f;

    public NpcPathFinder(NodeEvaluator nodeEvaluator, int maxVisitedNodes) {
        super(nodeEvaluator, maxVisitedNodes);
    }

    /**
     * Widened from {@code protected} to {@code public} — a legal override (Java allows increasing
     * visibility), needed so {@code NpcNavigationSelfTestCommand} (a different package; this jump
     * malus is pure {@link Node} math with no {@code Level}/{@code Mob} needed) can call it directly
     * instead of a test-only hook method.
     */
    @Override
    public float distance(Node from, Node to) {
        float base = super.distance(from, to);
        if (to.y > from.y) {
            base += JUMP_COST_MALUS_PER_BLOCK * (to.y - from.y);
        }
        return base;
    }
}
