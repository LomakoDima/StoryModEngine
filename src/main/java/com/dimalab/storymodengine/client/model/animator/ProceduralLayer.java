package com.dimalab.storymodengine.client.model.animator;

import com.dimalab.storymodengine.client.model.AnimationPose;
import com.dimalab.storymodengine.client.model.BonePose;
import com.dimalab.storymodengine.client.model.RuntimeNode;
import com.dimalab.storymodengine.client.model.animator.expr.AnimEvalContext;
import com.dimalab.storymodengine.client.model.animator.expr.AnimExpr;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Set;

/**
 * Poses named bones straight from expressions, with no clip behind them — additive infrastructure
 * alongside {@code HumanoidPoser} (SME's existing hand-written Java procedural poser), not a
 * replacement for it. {@code HumanoidPoser} keeps running exactly as before, via {@code
 * ModelInstance.poseWith}'s own callback.
 */
public final class ProceduralLayer extends SpecLayer {

    public ProceduralLayer(ProceduralLayerSpec spec) {
        super(spec);
    }

    private ProceduralLayerSpec proceduralSpec() {
        return (ProceduralLayerSpec) spec();
    }

    @Override
    protected boolean accepts(AnimatorLayerSpec spec) {
        return spec instanceof ProceduralLayerSpec;
    }

    @Override
    public LayerPose sample(PoseTarget target, AnimEvalContext context) {
        Set<Integer> allowed = mask(target);
        AnimationPose pose = new AnimationPose();

        for (ProceduralBoneTransformSpec transform : proceduralSpec().transforms()) {
            RuntimeNode node = target.node(transform.bone());
            if (node == null || !allowed.contains(node.definition().index())) {
                continue;
            }
            BonePose bone = pose.bone(node.definition().index());
            if (transform.translation() != null) {
                bone.translation = AnimExpr.evalVector(transform.translation(), context);
            }
            if (transform.rotation() != null) {
                Vector3f euler = AnimExpr.evalVector(transform.rotation(), context);
                // Rz * Ry * Rx, composed via JOML's post-multiply fluent rotate* calls from identity —
                // matches HollowEngine's own procedural-layer rotation composition order.
                bone.rotation = new Quaternionf()
                        .rotateZ((float) Math.toRadians(euler.z))
                        .rotateY((float) Math.toRadians(euler.y))
                        .rotateX((float) Math.toRadians(euler.x));
            }
            if (transform.scale() != null) {
                bone.scale = AnimExpr.evalVector(transform.scale(), context);
            }
        }
        return new LayerPose(pose);
    }
}
