package com.dimalab.storymodengine.common.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

/**
 * Vanilla's own {@code isDiagonalValid} (see {@code WalkNodeEvaluator.java}) refuses a diagonal move
 * whenever either flanking cardinal neighbor is blocked — safe, but more conservative than it needs
 * to be: a mob whose body is narrower than the corner gap can often walk straight through a diagonal
 * vanilla insists on detouring around. This adds exactly one more way for a diagonal to be accepted,
 * on top of — never instead of — vanilla's own check: real body-clearance geometry against the actual
 * collision boxes at that corner (see {@link NpcNavigationGeometry#canSqueezeDiagonally}). Vanilla's
 * check always runs first and short-circuits the common case unchanged, so this can only make more
 * corners cuttable, never break a path vanilla already accepted.
 */
public class NpcNodeEvaluator extends WalkNodeEvaluator {

    @Override
    protected boolean isDiagonalValid(Node node, Node neighborA, Node neighborB, Node diagonal) {
        if (super.isDiagonalValid(node, neighborA, neighborB, diagonal)) {
            return true;
        }
        if (diagonal == null || diagonal.closed || diagonal.costMalus < 0.0F) {
            return false;
        }
        return NpcNavigationGeometry.canSqueezeDiagonally(this.mob, this.level,
                new BlockPos(node.x, node.y, node.z), new BlockPos(diagonal.x, diagonal.y, diagonal.z));
    }
}
