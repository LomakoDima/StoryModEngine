package com.dimalab.storymodengine.api.math;

import org.joml.Vector3f;

/**
 * Small, general-purpose geometric queries that don't belong to any one curve/transform type —
 * kept deliberately short. {@code Mth.rayIntersectsAABB} already covers ray/AABB testing; this
 * adds what it doesn't: the closest point on a segment (useful for snapping a camera or an object
 * onto a path) and a ray/plane intersection (useful for cutscene targeting/look-at logic).
 */
public final class Geometry {

    private Geometry() {
    }

    /** The closest point to {@code point} lying on the segment {@code [a, b]}. */
    public static Vector3f closestPointOnSegment(Vector3f point, Vector3f a, Vector3f b) {
        Vector3f ab = new Vector3f(b).sub(a);
        float lengthSquared = ab.lengthSquared();
        if (lengthSquared < 1e-10f) {
            return new Vector3f(a);
        }
        float t = new Vector3f(point).sub(a).dot(ab) / lengthSquared;
        t = Math.min(Math.max(t, 0f), 1f);
        return a.fma(t, ab, new Vector3f());
    }

    /**
     * Where the ray {@code origin + direction * t} crosses the plane through {@code planePoint}
     * with normal {@code planeNormal}, or {@code null} if the ray is parallel to the plane (or
     * points away from it, since a ray only extends forward).
     */
    public static Vector3f rayPlaneIntersection(Vector3f origin, Vector3f direction, Vector3f planePoint, Vector3f planeNormal) {
        float denom = direction.dot(planeNormal);
        if (Math.abs(denom) < 1e-6f) {
            return null;
        }
        float t = new Vector3f(planePoint).sub(origin).dot(planeNormal) / denom;
        if (t < 0f) {
            return null;
        }
        return origin.fma(t, direction, new Vector3f());
    }
}
