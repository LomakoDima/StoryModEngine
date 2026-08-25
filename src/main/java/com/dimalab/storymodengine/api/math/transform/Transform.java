package com.dimalab.storymodengine.api.math.transform;

import com.mojang.math.Transformation;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * An immutable translation + rotation + scale — the transform type the rest of the engine (and
 * mod authors) should reach for. Minecraft already has {@code com.mojang.math.Transformation},
 * used by block/item display entities, but its two-quaternion "left rotation / right rotation"
 * shape exists specifically for the shearing that display-entity transforms support and isn't
 * something a camera path, an animated object, or a UI element needs to think about. {@code
 * Transform} is the plain single-rotation case of exactly the same math — {@link #toMojang()} and
 * {@link #from(Transformation)} convert losslessly to/from it (with the right rotation fixed at
 * identity), so nothing here duplicates {@code Transformation}'s compose/inverse/interpolate logic;
 * it delegates to it.
 *
 * <p>A record, not a builder — construct a new one to change any component. That's deliberate:
 * the whole {@code math} package treats values passed through curves/interpolators as immutable,
 * so a transform sampled off a curve can be handed around, compared, and reused without a caller
 * needing to worry about who else might mutate it. For a hot per-frame path, prefer sampling the
 * underlying JOML types directly with their {@code dest}-parameter overloads (see
 * {@code Interpolator#interpolate(Object, Object, float, Object)}) over allocating a fresh
 * {@code Transform} every frame.
 */
public record Transform(Vector3f translation, Quaternionf rotation, Vector3f scale) {

    public static final Transform IDENTITY = new Transform(new Vector3f(), new Quaternionf(), new Vector3f(1, 1, 1));

    public static Transform of(Vector3f translation, Quaternionf rotation, Vector3f scale) {
        return new Transform(translation, rotation, scale);
    }

    public static Transform translation(Vector3f translation) {
        return new Transform(translation, new Quaternionf(), new Vector3f(1, 1, 1));
    }

    public static Transform rotation(Quaternionf rotation) {
        return new Transform(new Vector3f(), rotation, new Vector3f(1, 1, 1));
    }

    public static Transform scaling(Vector3f scale) {
        return new Transform(new Vector3f(), new Quaternionf(), scale);
    }

    /** Converts to Minecraft's own transform type, right rotation fixed at identity (no shear). */
    public Transformation toMojang() {
        return new Transformation(new Vector3f(translation), new Quaternionf(rotation), new Vector3f(scale), new Quaternionf());
    }

    /** Drops any shear a {@code Transformation} carries in its right rotation — see the class doc. */
    public static Transform from(Transformation transformation) {
        return new Transform(
                new Vector3f(transformation.getTranslation()),
                new Quaternionf(transformation.getLeftRotation()),
                new Vector3f(transformation.getScale()));
    }

    public Matrix4f toMatrix() {
        return toMojang().getMatrix();
    }

    /**
     * {@code this} as the parent space, {@code child} as a transform expressed relative to it —
     * the standard local-to-world composition for a hierarchy (an object riding a moving platform,
     * a camera offset from a rig, a limb attached to a rig bone).
     */
    public Transform compose(Transform child) {
        return from(toMojang().compose(child.toMojang()));
    }

    /** The transform that undoes {@code this} — e.g. to convert a world-space point into this transform's local space. */
    public Transform inverse() {
        return from(toMojang().inverse());
    }
}
