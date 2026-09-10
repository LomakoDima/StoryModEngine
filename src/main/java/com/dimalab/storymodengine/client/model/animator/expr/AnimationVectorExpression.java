package com.dimalab.storymodengine.client.model.animator.expr;

/** Three independent scalar expressions — one per axis — for a procedural bone's translation/rotation/scale. */
public record AnimationVectorExpression(AnimationExpression x, AnimationExpression y, AnimationExpression z) {

    public static final AnimationVectorExpression ZERO =
            new AnimationVectorExpression(AnimationExpression.ZERO, AnimationExpression.ZERO, AnimationExpression.ZERO);
    public static final AnimationVectorExpression ONE =
            new AnimationVectorExpression(AnimationExpression.ONE, AnimationExpression.ONE, AnimationExpression.ONE);

    public static AnimationVectorExpression of(String x, String y, String z) {
        return new AnimationVectorExpression(AnimationExpression.of(x), AnimationExpression.of(y), AnimationExpression.of(z));
    }
}
