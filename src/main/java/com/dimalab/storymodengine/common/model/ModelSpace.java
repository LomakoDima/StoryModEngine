package com.dimalab.storymodengine.common.model;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

/**
 * Brings an imported hierarchy into the space Minecraft models live in: Y up, one unit per block,
 * front facing −Z.
 *
 * <p>glTF's own convention puts a model's front at <b>+Z</b>, so a model authored in Blender/Maya/
 * 3ds Max and exported to glTF faces the opposite way from every vanilla Minecraft model — it would
 * render back-to-front with no correction. Blockbench is the exception: it authors in Minecraft's own
 * orientation already, and stamps its name into {@code asset.generator}, which is how {@link
 * #needsFacingCorrection(String)} tells the two apart rather than guessing or making the mod author
 * configure it.
 *
 * <p>The correction is applied as a <b>synthetic root node</b> wrapping the real ones, not baked into
 * vertex data — so the original geometry stays exactly as authored, skinning still works against the
 * unmodified bind pose, and the correction composes through the normal parent-chain math like any
 * other transform.
 */
public final class ModelSpace {

    /** Index of the synthetic correction node. Negative so it can never collide with a real glTF node index. */
    public static final int ROOT_INDEX = -1;

    private ModelSpace() {
    }

    /** True for everything except Blockbench — see the class doc. A file with no {@code asset.generator} is assumed to be a normal glTF exporter. */
    public static boolean needsFacingCorrection(String generator) {
        return generator == null || !generator.toLowerCase(java.util.Locale.ROOT).contains("blockbench");
    }

    /**
     * Whether to correct, honouring an explicit {@code facing} from the model's {@code .smemeta}
     * before falling back to sniffing {@code asset.generator}. The override exists because that sniff
     * is a heuristic over a free-text field — an unknown exporter, or one whose string a converter
     * rewrote, can simply be told the answer.
     */
    public static boolean needsFacingCorrection(String generator, ModelMetadata.Facing facing) {
        return switch (facing) {
            case GLTF -> true;
            case MINECRAFT -> false;
            case AUTO -> needsFacingCorrection(generator);
        };
    }

    /**
     * Wraps {@code roots} in a correction node when one is needed, or returns them untouched when
     * the source already matches Minecraft's orientation and scale.
     */
    public static List<ModelNode> place(List<ModelNode> roots, boolean facesPositiveZ, float unitsPerBlock) {
        if (roots.isEmpty() || (!facesPositiveZ && unitsPerBlock == 1f)) {
            return roots;
        }

        Quaternionf rotation = facesPositiveZ
                ? new Quaternionf().rotateY((float) Math.PI)
                : new Quaternionf();
        float s = 1f / unitsPerBlock;

        ModelNode correction = new ModelNode(ROOT_INDEX, "__model_space__",
                new Vector3f(), rotation, new Vector3f(s, s, s), null, null);
        for (ModelNode root : roots) {
            correction.addChild(root);
        }
        return List.of(correction);
    }
}
