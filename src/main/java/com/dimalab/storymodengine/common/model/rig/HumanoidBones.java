package com.dimalab.storymodengine.common.model.rig;

/**
 * The bone names procedural humanoid animation addresses.
 *
 * <p>This is a <b>convention</b>, not a rig: the rig itself — which of a model's parts belong to each
 * bone, and where each joint pivots — lives in that model's own {@code .smemeta} sidecar. All this
 * fixes is the vocabulary both sides agree on, so {@code client.model.HumanoidPoser} can drive any
 * model whose sidecar uses these names, and a new humanoid needs no Java at all.
 *
 * <p>An earlier version had the whole rig here as Java constants, naming one specific asset's parts
 * ({@code golova}, {@code Helmet}, {@code leftHand_layer}) inside engine code — so a second model
 * with different part names meant editing and recompiling the engine.
 */
public final class HumanoidBones {

    public static final String ROOT = "root";
    public static final String BODY = "body";
    public static final String HEAD = "head";
    public static final String LEFT_ARM = "leftArm";
    public static final String RIGHT_ARM = "rightArm";
    public static final String LEFT_LEG = "leftLeg";
    public static final String RIGHT_LEG = "rightLeg";

    private HumanoidBones() {
    }
}
