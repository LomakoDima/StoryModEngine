package com.dimalab.storymodengine.client.model.animator;

import com.dimalab.storymodengine.client.model.animator.expr.AnimationVectorExpression;

/** Poses one named bone directly from expressions — any of the three components may be null (untouched). */
public record ProceduralBoneTransformSpec(
        String bone,
        AnimationVectorExpression translation,
        AnimationVectorExpression rotation,
        AnimationVectorExpression scale
) {
}
