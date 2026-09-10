package com.dimalab.storymodengine.common.model;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * One node of the model's hierarchy — the shared, immutable half of a bone. glTF's node graph *is*
 * the model's structure (a mesh's placement, a bone's rest pose, and the parent chain skinning walks
 * are all expressed by it), so it's preserved here as a real tree rather than flattened away.
 *
 * <p>The TRS stored here is the <b>bind pose</b>: the node's rest transform relative to its parent.
 * Per-entity animated state never touches this object — it lives in {@code client.model.RuntimeNode},
 * one per rendered instance, which starts from these values and is re-derived every frame. Same
 * "one definition, many independent runtimes" split {@code flow.Flow}/{@code FlowManager} already use.
 */
public final class ModelNode {

    private final int index;
    private final String name;
    private final Vector3f bindTranslation;
    private final Quaternionf bindRotation;
    private final Vector3f bindScale;
    private final Mesh mesh;
    private final Skin skin;
    private final float[] defaultMorphWeights;
    private final List<ModelNode> children = new ArrayList<>();
    private ModelNode parent;

    public ModelNode(int index, String name, Vector3f bindTranslation, Quaternionf bindRotation, Vector3f bindScale, Mesh mesh, Skin skin) {
        this(index, name, bindTranslation, bindRotation, bindScale, mesh, skin, new float[0]);
    }

    /** @param defaultMorphWeights this node's morph weights at rest — the node's own {@code weights} override if it declared one, else its mesh's default, else empty. */
    public ModelNode(int index, String name, Vector3f bindTranslation, Quaternionf bindRotation, Vector3f bindScale, Mesh mesh, Skin skin, float[] defaultMorphWeights) {
        this.index = index;
        this.name = name;
        this.bindTranslation = bindTranslation;
        this.bindRotation = bindRotation;
        this.bindScale = bindScale;
        this.mesh = mesh;
        this.skin = skin;
        this.defaultMorphWeights = defaultMorphWeights;
    }

    public void addChild(ModelNode child) {
        children.add(child);
        child.parent = this;
    }

    /**
     * Swaps one child for another in the same position — used when a node has to be rebuilt (e.g.
     * renamed, since {@link #name} is immutable) without disturbing the rest of the tree around it.
     */
    public void replaceChild(ModelNode oldChild, ModelNode newChild) {
        int index = children.indexOf(oldChild);
        if (index < 0) {
            throw new IllegalArgumentException(oldChild + " is not a child of " + this);
        }
        children.set(index, newChild);
        newChild.parent = this;
    }

    public int index() {
        return index;
    }

    public String name() {
        return name;
    }

    /** The bind-pose translation. Treat as read-only — every runtime instance shares this instance. */
    public Vector3f bindTranslation() {
        return bindTranslation;
    }

    public Quaternionf bindRotation() {
        return bindRotation;
    }

    public Vector3f bindScale() {
        return bindScale;
    }

    public Mesh mesh() {
        return mesh;
    }

    public Skin skin() {
        return skin;
    }

    public float[] defaultMorphWeights() {
        return defaultMorphWeights;
    }

    public List<ModelNode> children() {
        return children;
    }

    public ModelNode parent() {
        return parent;
    }

    @Override
    public String toString() {
        return "ModelNode[" + index + " '" + name + "'" + (mesh != null ? " mesh" : "") + (skin != null ? " skin" : "") + "]";
    }
}
