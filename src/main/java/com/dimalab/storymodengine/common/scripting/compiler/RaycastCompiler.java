package com.dimalab.storymodengine.common.scripting.compiler;

import com.dimalab.storymodengine.api.flow.Evaluator;
import com.dimalab.storymodengine.common.raycast.Raycast;
import com.dimalab.storymodengine.common.scripting.ast.RaycastIfStmtNode;
import com.dimalab.storymodengine.common.scripting.ast.RaycastTarget;

/** {@code RaycastIfStmtNode}'s condition → an {@code Evaluator<Boolean>} wrapping the existing {@code Raycast.from(...)} query API — no new raycast implementation. */
public final class RaycastCompiler {

    private RaycastCompiler() {
    }

    public static Evaluator<Boolean> compileCondition(RaycastIfStmtNode node) {
        RaycastTarget target = node.target();
        int distance = node.distance();
        boolean livingOnly = node.livingOnly();
        return fc -> {
            var query = Raycast.from(fc.player()).distance(distance);
            switch (target) {
                case ENTITY -> query.entities();
                case BLOCK -> query.blocks();
                case ANY -> query.any();
            }
            if (livingOnly) {
                query.livingEntities();
            }
            return query.cast().isHit();
        };
    }
}
