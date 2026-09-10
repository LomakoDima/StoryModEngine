package com.dimalab.storymodengine.client.model;

import com.dimalab.storymodengine.common.model.ModelNode;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The per-instance half of a bone: one {@link ModelNode} definition (shared, immutable) plus this
 * particular rendered instance's own current pose. Two entities showing the same model get two
 * independent {@code RuntimeNode} trees over one shared definition tree — the same split {@code
 * flow.Flow}/{@code FlowManager} already use for "one definition, many runs".
 *
 * <p>Everything here is mutable and reused: the pose is written in place every frame, and {@link
 * #updateHierarchy()} composes matrices with JOML's dest-parameter overloads, so a full hierarchy
 * update allocates nothing.
 */
public final class RuntimeNode {

    private final ModelNode definition;
    private final RuntimeNode parent;
    private final List<RuntimeNode> children = new ArrayList<>();

    private final Vector3f translation = new Vector3f();
    private final Quaternionf rotation = new Quaternionf();
    private final Vector3f scale = new Vector3f(1f, 1f, 1f);
    private final float[] morphWeights;

    private final Matrix4f localMatrix = new Matrix4f();
    private final Matrix4f globalMatrix = new Matrix4f();

    /** Dotted ancestor-name chain, computed once at construction — see {@link #path()}. */
    private final String path;

    public RuntimeNode(ModelNode definition, RuntimeNode parent) {
        this.definition = definition;
        this.parent = parent;
        this.path = parent == null ? definition.name() : parent.path + "." + definition.name();
        this.morphWeights = definition.defaultMorphWeights().clone();
        resetPose();
        for (ModelNode child : definition.children()) {
            children.add(new RuntimeNode(child, this));
        }
    }

    public ModelNode definition() {
        return definition;
    }

    /** Null for a root node. */
    public RuntimeNode parent() {
        return parent;
    }

    public String name() {
        return definition.name();
    }

    /**
     * The full dotted ancestor-name chain from the root down to this node (e.g. {@code
     * "Body.RightArm.RightHand"}), used by {@code animator.PoseTarget}'s bone-mask matching. A
     * deliberate deviation from HollowEngine's own {@code NodeDefinition.path}, which is one level
     * only ({@code parent.name + "/" + name}) — a full chain is a strict superset for {@code
     * endsWith} suffix matching and more useful on deep rigs.
     */
    public String path() {
        return path;
    }

    public List<RuntimeNode> children() {
        return children;
    }

    public Vector3f translation() {
        return translation;
    }

    public Quaternionf rotation() {
        return rotation;
    }

    public Vector3f scale() {
        return scale;
    }

    /** This node's live morph target weights, one per {@code Primitive.MorphTarget} its mesh's primitives declare — mutable in place, same style as {@link #translation()}/{@link #rotation()}/{@link #scale()}. */
    public float[] morphWeights() {
        return morphWeights;
    }

    /** This node's transform in model space — valid only after {@link #updateHierarchy()} has run for this frame. */
    public Matrix4f globalMatrix() {
        return globalMatrix;
    }

    /** Restores the bind pose. Called at the start of every frame's posing pass, so a clip that stops animating a bone lets it fall back to rest instead of freezing at its last animated value. */
    public void resetPose() {
        translation.set(definition.bindTranslation());
        rotation.set(definition.bindRotation());
        scale.set(definition.bindScale());
        System.arraycopy(definition.defaultMorphWeights(), 0, morphWeights, 0, morphWeights.length);
    }

    public void resetPoseRecursive() {
        resetPose();
        for (RuntimeNode child : children) {
            child.resetPoseRecursive();
        }
    }

    /** Recomputes {@code globalMatrix} for this node and everything under it: {@code global = parent.global × local}, top-down in one pass. */
    public void updateHierarchy() {
        localMatrix.translationRotateScale(translation, rotation, scale);
        if (parent == null) {
            globalMatrix.set(localMatrix);
        } else {
            parent.globalMatrix.mul(localMatrix, globalMatrix);
        }
        for (RuntimeNode child : children) {
            child.updateHierarchy();
        }
    }

    public void index(Map<Integer, RuntimeNode> out) {
        out.put(definition.index(), this);
        for (RuntimeNode child : children) {
            child.index(out);
        }
    }
}
