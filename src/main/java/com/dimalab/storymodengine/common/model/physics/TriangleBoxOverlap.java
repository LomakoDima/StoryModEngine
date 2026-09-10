package com.dimalab.storymodengine.common.model.physics;

import org.joml.Vector3f;

/**
 * Exact triangle vs. axis-aligned-box overlap, by the separating axis theorem (the standard
 * Akenine-Möller formulation: nine edge-cross-axis tests, three box-face tests, one triangle-plane
 * test).
 *
 * <p>Exactness matters here even though the result feeds a blocky approximation. The cheap
 * alternative — marking every cell a triangle's own bounding box touches — over-fills badly for
 * diagonal geometry (a sloped roof would collide as a solid wedge), while sampling points along the
 * triangle under-fills and leaves cells a thin wall passes through unmarked, producing collision the
 * player can walk through. Both failure modes are the kind that only show up as "the collision feels
 * wrong somewhere", so the exact test is worth its sixty lines.
 */
final class TriangleBoxOverlap {

    private TriangleBoxOverlap() {
    }

    static boolean test(Vector3f a, Vector3f b, Vector3f c, Vector3f boxCenter, float halfX, float halfY, float halfZ) {
        // Move the triangle into the box's local frame, box centered at the origin.
        float v0x = a.x - boxCenter.x, v0y = a.y - boxCenter.y, v0z = a.z - boxCenter.z;
        float v1x = b.x - boxCenter.x, v1y = b.y - boxCenter.y, v1z = b.z - boxCenter.z;
        float v2x = c.x - boxCenter.x, v2y = c.y - boxCenter.y, v2z = c.z - boxCenter.z;

        float e0x = v1x - v0x, e0y = v1y - v0y, e0z = v1z - v0z;
        float e1x = v2x - v1x, e1y = v2y - v1y, e1z = v2z - v1z;
        float e2x = v0x - v2x, e2y = v0y - v2y, e2z = v0z - v2z;

        // 9 axis tests: each triangle edge crossed with each box axis.
        if (!axisTest(e0z, e0y, v0y, v0z, v2y, v2z, halfY, halfZ)) return false;
        if (!axisTest(e0z, e0x, v0x, v0z, v2x, v2z, halfX, halfZ)) return false;
        if (!axisTest(e0y, e0x, v1x, v1y, v2x, v2y, halfX, halfY)) return false;

        if (!axisTest(e1z, e1y, v0y, v0z, v2y, v2z, halfY, halfZ)) return false;
        if (!axisTest(e1z, e1x, v0x, v0z, v2x, v2z, halfX, halfZ)) return false;
        if (!axisTest(e1y, e1x, v0x, v0y, v1x, v1y, halfX, halfY)) return false;

        if (!axisTest(e2z, e2y, v0y, v0z, v1y, v1z, halfY, halfZ)) return false;
        if (!axisTest(e2z, e2x, v0x, v0z, v1x, v1z, halfX, halfZ)) return false;
        if (!axisTest(e2y, e2x, v1x, v1y, v2x, v2y, halfX, halfY)) return false;

        // 3 box-face tests: the triangle's own AABB must overlap the box on every axis.
        if (Math.min(v0x, Math.min(v1x, v2x)) > halfX || Math.max(v0x, Math.max(v1x, v2x)) < -halfX) return false;
        if (Math.min(v0y, Math.min(v1y, v2y)) > halfY || Math.max(v0y, Math.max(v1y, v2y)) < -halfY) return false;
        if (Math.min(v0z, Math.min(v1z, v2z)) > halfZ || Math.max(v0z, Math.max(v1z, v2z)) < -halfZ) return false;

        // 1 plane test: the box must straddle the triangle's plane.
        float nx = e0y * e1z - e0z * e1y;
        float ny = e0z * e1x - e0x * e1z;
        float nz = e0x * e1y - e0y * e1x;
        float d = -(nx * v0x + ny * v0y + nz * v0z);
        float radius = Math.abs(nx) * halfX + Math.abs(ny) * halfY + Math.abs(nz) * halfZ;
        return Math.abs(d) <= radius;
    }

    /**
     * One separating-axis test. {@code p1}/{@code p2} are the two distinct projections of the
     * triangle's vertices onto the candidate axis (the third always coincides with one of them), and
     * {@code radius} is the box's extent along it.
     */
    private static boolean axisTest(float aComponent, float bComponent,
                                    float p1u, float p1v, float p2u, float p2v,
                                    float halfU, float halfV) {
        float p1 = aComponent * p1u - bComponent * p1v;
        float p2 = aComponent * p2u - bComponent * p2v;
        float min = Math.min(p1, p2);
        float max = Math.max(p1, p2);
        float radius = Math.abs(aComponent) * halfU + Math.abs(bComponent) * halfV;
        return min <= radius && max >= -radius;
    }
}
