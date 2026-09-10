package com.dimalab.storymodengine.client.model.animator;

import com.dimalab.storymodengine.api.model.LayerBlendMode;
import com.dimalab.storymodengine.client.model.animator.expr.AnimationExpression;

/**
 * What one layer is and how it composes — a plain data description, built by Java code and handed to
 * {@code ModelAnimator.addLayer}. No JSON (de)serialization and no editor-support fields (HollowEngine's
 * own spec types carry a {@code layout}/{@code GraphPoint} field purely for its visual node-graph
 * editor — this project builds no editor, so there is nothing analogous here).
 */
public sealed interface AnimatorLayerSpec permits ClipAnimationLayerSpec, AnimationControllerLayerSpec, ProceduralLayerSpec {
    String id();

    AnimationExpression weight();

    int priority();

    LayerBlendMode blendMode();

    BoneMask mask();

    float fadeIn();

    float fadeOut();
}
