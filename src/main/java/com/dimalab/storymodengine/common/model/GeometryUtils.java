package com.dimalab.storymodengine.common.model;

import org.joml.Vector3f;

/**
 * Fills in vertex attributes a source file legitimately may omit. glTF makes {@code NORMAL}
 * optional, and the spec's own fallback ("when normals are not specified, client implementations
 * MUST calculate flat normals") is what {@link #recalculateNormals} implements — accumulate each
 * triangle's face normal onto its three vertices, then normalize, which yields flat normals for
 * hard-edged geometry and smooth ones wherever vertices are shared. Previously a missing normal was
 * substituted with a constant {@code (0, 1, 0)}, which lit every such model as though it were a
 * floor.
 */
public final class GeometryUtils {

    private GeometryUtils() {
    }

    public static float[] recalculateNormals(float[] positions, int[] indices) {
        float[] normals = new float[positions.length];
        Vector3f a = new Vector3f();
        Vector3f b = new Vector3f();
        Vector3f c = new Vector3f();
        Vector3f edge1 = new Vector3f();
        Vector3f edge2 = new Vector3f();
        Vector3f faceNormal = new Vector3f();

        for (int i = 0; i + 2 < indices.length; i += 3) {
            int i0 = indices[i];
            int i1 = indices[i + 1];
            int i2 = indices[i + 2];
            a.set(positions[i0 * 3], positions[i0 * 3 + 1], positions[i0 * 3 + 2]);
            b.set(positions[i1 * 3], positions[i1 * 3 + 1], positions[i1 * 3 + 2]);
            c.set(positions[i2 * 3], positions[i2 * 3 + 1], positions[i2 * 3 + 2]);

            b.sub(a, edge1);
            c.sub(a, edge2);
            edge1.cross(edge2, faceNormal);

            for (int index : new int[]{i0, i1, i2}) {
                normals[index * 3] += faceNormal.x;
                normals[index * 3 + 1] += faceNormal.y;
                normals[index * 3 + 2] += faceNormal.z;
            }
        }

        for (int i = 0; i + 2 < normals.length; i += 3) {
            faceNormal.set(normals[i], normals[i + 1], normals[i + 2]);
            if (faceNormal.lengthSquared() > 1.0e-12f) {
                faceNormal.normalize();
            } else {
                faceNormal.set(0f, 1f, 0f);
            }
            normals[i] = faceNormal.x;
            normals[i + 1] = faceNormal.y;
            normals[i + 2] = faceNormal.z;
        }
        return normals;
    }

    private static final float TANGENT_EPSILON = 1.0e-8f;

    /**
     * Per-vertex tangents (xyz + w handedness, per spec) for a mesh whose file carries none —
     * ported from HollowEngine's own {@code GeometryUtils.recalculateTangents}, same accumulate-
     * then-orthogonalize shape: each triangle's tangent/bitangent come from its UV deltas (Lengyel's
     * method), get accumulated per vertex weighted by triangle area, and are finally Gram-Schmidt
     * orthogonalized against that vertex's normal and normalized. Handedness is the sign of
     * {@code dot(cross(normal, tangent), accumulatedBitangent)}.
     *
     * <p>A triangle whose UV mapping is degenerate (near-zero determinant or area) contributes
     * nothing rather than injecting NaNs/infinities into its vertices' accumulators — the same guard
     * HollowEngine's own version applies.
     */
    public static float[] recalculateTangents(float[] positions, float[] normals, float[] uv, int[] indices) {
        int vertexCount = positions.length / 3;
        float[] tangents = new float[vertexCount * 4];
        if (vertexCount == 0) {
            return tangents;
        }

        Vector3f[] accumulatedTangents = new Vector3f[vertexCount];
        Vector3f[] accumulatedBitangents = new Vector3f[vertexCount];
        for (int i = 0; i < vertexCount; i++) {
            accumulatedTangents[i] = new Vector3f();
            accumulatedBitangents[i] = new Vector3f();
        }

        for (int e = 0; e + 2 < indices.length; e += 3) {
            int i0 = indices[e];
            int i1 = indices[e + 1];
            int i2 = indices[e + 2];

            float p0x = positions[i0 * 3], p0y = positions[i0 * 3 + 1], p0z = positions[i0 * 3 + 2];
            float p1x = positions[i1 * 3], p1y = positions[i1 * 3 + 1], p1z = positions[i1 * 3 + 2];
            float p2x = positions[i2 * 3], p2y = positions[i2 * 3 + 1], p2z = positions[i2 * 3 + 2];

            float uv0x = uv[i0 * 2], uv0y = uv[i0 * 2 + 1];
            float uv1x = uv[i1 * 2], uv1y = uv[i1 * 2 + 1];
            float uv2x = uv[i2 * 2], uv2y = uv[i2 * 2 + 1];

            float e1x = p1x - p0x, e1y = p1y - p0y, e1z = p1z - p0z;
            float e2x = p2x - p0x, e2y = p2y - p0y, e2z = p2z - p0z;

            float du1 = uv1x - uv0x, dv1 = uv1y - uv0y;
            float du2 = uv2x - uv0x, dv2 = uv2y - uv0y;

            float determinant = du1 * dv2 - du2 * dv1;
            if (!Float.isFinite(determinant) || Math.abs(determinant) <= TANGENT_EPSILON) {
                continue;
            }

            float faceX = e1y * e2z - e1z * e2y;
            float faceY = e1z * e2x - e1x * e2z;
            float faceZ = e1x * e2y - e1y * e2x;
            float areaWeight = (float) Math.sqrt(faceX * faceX + faceY * faceY + faceZ * faceZ);
            if (!Float.isFinite(areaWeight) || areaWeight <= TANGENT_EPSILON) {
                continue;
            }

            float invDet = 1f / determinant;
            float tx = (dv2 * e1x - dv1 * e2x) * invDet;
            float ty = (dv2 * e1y - dv1 * e2y) * invDet;
            float tz = (dv2 * e1z - dv1 * e2z) * invDet;
            float bx = (du1 * e2x - du2 * e1x) * invDet;
            float by = (du1 * e2y - du2 * e1y) * invDet;
            float bz = (du1 * e2z - du2 * e1z) * invDet;

            if (!Float.isFinite(tx) || !Float.isFinite(ty) || !Float.isFinite(tz)
                    || !Float.isFinite(bx) || !Float.isFinite(by) || !Float.isFinite(bz)) {
                continue;
            }

            for (int i : new int[]{i0, i1, i2}) {
                accumulatedTangents[i].add(tx * areaWeight, ty * areaWeight, tz * areaWeight);
                accumulatedBitangents[i].add(bx * areaWeight, by * areaWeight, bz * areaWeight);
            }
        }

        Vector3f normal = new Vector3f();
        Vector3f tangent = new Vector3f();
        for (int i = 0; i < vertexCount; i++) {
            normal.set(normals[i * 3], normals[i * 3 + 1], normals[i * 3 + 2]);
            if (normal.lengthSquared() <= TANGENT_EPSILON) {
                normal.set(0f, 1f, 0f);
            } else {
                normal.normalize();
            }

            tangent.set(accumulatedTangents[i]);
            orthogonalizeOrFallback(normal, tangent);

            float handedness = normal.cross(tangent, new Vector3f()).dot(accumulatedBitangents[i]) < 0f ? -1f : 1f;

            tangents[i * 4] = tangent.x;
            tangents[i * 4 + 1] = tangent.y;
            tangents[i * 4 + 2] = tangent.z;
            tangents[i * 4 + 3] = handedness;
        }
        return tangents;
    }

    /**
     * Projects {@code tangent} onto the plane perpendicular to {@code normal} (Gram-Schmidt) and
     * normalizes it in place; if the result is degenerate (near-zero length, or non-finite from a
     * NaN/infinite input) or {@code normal} itself is degenerate, substitutes a normal fallback
     * (a fixed perpendicular normal, or an arbitrary axis perpendicular to a valid one) instead of
     * ever leaving a zero or non-finite vector. Shared by {@link #recalculateTangents} (correcting
     * its own accumulator) and {@link #sanitizeTangents} (correcting a glTF file's own TANGENT
     * accessor data, which this importer otherwise trusts completely unvalidated).
     */
    private static void orthogonalizeOrFallback(Vector3f normal, Vector3f tangent) {
        if (!Float.isFinite(normal.x) || !Float.isFinite(normal.y) || !Float.isFinite(normal.z)
                || normal.lengthSquared() <= TANGENT_EPSILON) {
            normal.set(0f, 1f, 0f);
        } else {
            normal.normalize();
        }

        if (Float.isFinite(tangent.x) && Float.isFinite(tangent.y) && Float.isFinite(tangent.z)) {
            float normalDotTangent = normal.dot(tangent);
            tangent.sub(new Vector3f(normal).mul(normalDotTangent));
        } else {
            tangent.set(0f, 0f, 0f);
        }

        if (!Float.isFinite(tangent.x) || !Float.isFinite(tangent.y) || !Float.isFinite(tangent.z)
                || tangent.lengthSquared() <= TANGENT_EPSILON) {
            // The tangent vanished or was never valid (an isolated/degenerately-UV'd vertex, or —
            // for file-provided data — an exporter that simply wrote garbage for some vertices) —
            // fall back to any axis perpendicular to the normal rather than leave a zero/NaN vector
            // for a shader's own normalize() to turn into NaN-poisoned shading.
            if (Math.abs(normal.x) < 0.9f) {
                tangent.set(0f, -normal.z, normal.y);
            } else {
                tangent.set(normal.z, 0f, -normal.x);
            }
        }
        tangent.normalize();
    }

    /**
     * Checks (without mutating anything) whether a glTF file's own {@code TANGENT} accessor has any
     * vertex {@link #orthogonalizeOrFallback} would need to repair — near-zero length or non-finite
     * xyz. Exists specifically so the importer can decide {@link #sanitizeTangents} vs. discarding the
     * file data entirely in favor of {@link #recalculateTangents} <em>before</em> committing to either
     * — see {@code GltfMeshImporter}'s own doc on why a per-vertex patch is the wrong choice once any
     * vertex needs one.
     */
    public static boolean hasDegenerateTangent(float[] tangents) {
        int vertexCount = tangents.length / 4;
        for (int i = 0; i < vertexCount; i++) {
            float x = tangents[i * 4], y = tangents[i * 4 + 1], z = tangents[i * 4 + 2];
            if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)
                    || (x * x + y * y + z * z) <= TANGENT_EPSILON) {
                return true;
            }
        }
        return false;
    }

    /**
     * Final safety pass for a glTF file's own {@code TANGENT} accessor data, called only once {@link
     * #hasDegenerateTangent} has already confirmed <em>no</em> vertex needs the arbitrary-axis
     * fallback inside {@link #orthogonalizeOrFallback} — see {@code GltfMeshImporter}'s own doc for
     * why that fallback must never run on file data that's otherwise mostly valid (it did, briefly,
     * before this method's own caller was restructured — a converted rig shipped a few degenerate
     * entries among otherwise-correct, UV-derived ones, and patching only those with an
     * axis-relative-to-normal vector produced a real, per-vertex seam invisible to ordinary
     * tangent-space normal mapping but glaring under BSL's own iterative parallax ray-march, which
     * needs a <em>locally consistent</em> basis to step through texture space correctly). What
     * remains a genuine, worthwhile safety net even for confirmed-non-degenerate data: re-
     * orthogonalizing against the vertex's own normal (a file's tangent is rarely perfectly
     * perpendicular to it) and normalizing, plus guarding {@code w} (handedness) against a non-finite
     * value. {@code w} is otherwise preserved as authored.
     */
    public static float[] sanitizeTangents(float[] tangents, float[] normals) {
        int vertexCount = tangents.length / 4;
        Vector3f normal = new Vector3f();
        Vector3f tangent = new Vector3f();
        for (int i = 0; i < vertexCount; i++) {
            normal.set(normals[i * 3], normals[i * 3 + 1], normals[i * 3 + 2]);
            tangent.set(tangents[i * 4], tangents[i * 4 + 1], tangents[i * 4 + 2]);
            orthogonalizeOrFallback(normal, tangent);

            float w = tangents[i * 4 + 3];
            tangents[i * 4] = tangent.x;
            tangents[i * 4 + 1] = tangent.y;
            tangents[i * 4 + 2] = tangent.z;
            tangents[i * 4 + 3] = Float.isFinite(w) ? Math.signum(w != 0f ? w : 1f) : 1f;
        }
        return tangents;
    }

    /**
     * Validates and repairs a glTF file's own {@code JOINTS_0}/{@code WEIGHTS_0} data in place — the
     * skinning-attribute counterpart to {@link #sanitizeTangents} above, closing the same class of gap
     * for a different attribute pair. Two independent problems, both real and both unguarded anywhere
     * else in this pipeline before this method: a {@code JOINTS_0} index that is negative or {@code
     * &gt;= jointCount} is undefined behavior once it reaches {@code texelFetch} in {@code
     * gltf_skin_morph.vsh} (clamped into range here instead); a {@code WEIGHTS_0} vector that is
     * negative or sums to ~0 produces that same shader's {@code 0/0} NaN on the skinned position (see
     * its own doc) — normalized to sum to exactly 1 here, and a vertex whose weights are all
     * non-positive is rebound fully onto joint 0 rather than left with nothing to skin it at all.
     *
     * <p>{@code CpuSkinner} (the reference formula this shader is meant to match) already tolerates
     * both cases gracefully by skipping the bad component — but that class is test-only and never
     * renders anything; the real GPU path had no equivalent guard at all until this method.
     */
    public static void sanitizeSkinning(int[] joints, float[] weights, int jointCount) {
        int vertexCount = weights.length / 4;
        for (int i = 0; i < vertexCount; i++) {
            float w0 = Float.isFinite(weights[i * 4]) ? Math.max(0f, weights[i * 4]) : 0f;
            float w1 = Float.isFinite(weights[i * 4 + 1]) ? Math.max(0f, weights[i * 4 + 1]) : 0f;
            float w2 = Float.isFinite(weights[i * 4 + 2]) ? Math.max(0f, weights[i * 4 + 2]) : 0f;
            float w3 = Float.isFinite(weights[i * 4 + 3]) ? Math.max(0f, weights[i * 4 + 3]) : 0f;
            float sum = w0 + w1 + w2 + w3;
            if (!(sum > 1.0e-6f)) {
                joints[i * 4] = 0;
                joints[i * 4 + 1] = 0;
                joints[i * 4 + 2] = 0;
                joints[i * 4 + 3] = 0;
                weights[i * 4] = 1f;
                weights[i * 4 + 1] = 0f;
                weights[i * 4 + 2] = 0f;
                weights[i * 4 + 3] = 0f;
                continue;
            }
            weights[i * 4] = w0 / sum;
            weights[i * 4 + 1] = w1 / sum;
            weights[i * 4 + 2] = w2 / sum;
            weights[i * 4 + 3] = w3 / sum;
            for (int c = 0; c < 4; c++) {
                int idx = joints[i * 4 + c];
                joints[i * 4 + c] = jointCount > 0 ? Math.max(0, Math.min(jointCount - 1, idx)) : 0;
            }
        }
    }

    /**
     * Per-vertex UV averaged over every triangle's centroid that touches it — ported from
     * HollowEngine's {@code GeometryUtils.recalculateMidCoords}, which (per its own doc) computes and
     * even uploads this to a GPU buffer but never actually binds it to a shader attribute, so it has
     * no effect there either.
     *
     * <p><b>Not what {@code GpuSkinBuffers}/{@code InstanceBatchBuffers} actually feed the OptiFine/
     * Iris-standard {@code mc_midTexCoord} attribute, despite looking like the obvious fit — tried
     * first, and it made a real bug worse.</b> BSL's real {@code gbuffers_entities.glsl} (extracted
     * and read directly), under its own "Advanced Materials" option, uses {@code mc_midTexCoord} to
     * compute an atlas-relative parallax remap ({@code newCoord = vTexCoord.st * vTexCoordAM.pq +
     * vTexCoordAM.st}, derived from {@code mc_midTexCoord - texCoord}) — a remap that only makes sense
     * for an atlased terrain texture, and is meaningless for this engine's standalone per-material
     * textures. Feeding this method's own per-triangle-averaged result made {@code mc_midTexCoord}
     * genuinely <em>vary</em> across a surface that should read as one continuous material, producing
     * a real discontinuity in BSL's remap at every triangle edge — a faceted/triangular look, worse
     * than leaving the attribute unfed. The actual fix both classes use: bind the exact same buffer as
     * {@code UV0} to {@code mc_midTexCoord} too. That makes {@code mc_midTexCoord - texCoord} exactly
     * {@code 0} everywhere, which is a provable identity in BSL's own formula above — {@code newCoord}
     * reduces to plain {@code texCoord}, disabling the remap rather than feeding it a wrong value.
     * Matches HollowEngine's own proven fallback ({@code PipelineRenderer.kt}: {@code midBuffer ?:
     * uvBuffer}). This method itself remains unused — kept as a correct, tested building block, not a
     * feature that changes how anything renders today.
     */
    public static float[] recalculateMidUv(float[] uv, int[] indices) {
        int vertexCount = uv.length / 2;
        float[] accumulated = new float[vertexCount * 2];
        int[] contributions = new int[vertexCount];

        for (int e = 0; e + 2 < indices.length; e += 3) {
            int i0 = indices[e];
            int i1 = indices[e + 1];
            int i2 = indices[e + 2];
            float midU = (uv[i0 * 2] + uv[i1 * 2] + uv[i2 * 2]) / 3f;
            float midV = (uv[i0 * 2 + 1] + uv[i1 * 2 + 1] + uv[i2 * 2 + 1]) / 3f;
            for (int i : new int[]{i0, i1, i2}) {
                accumulated[i * 2] += midU;
                accumulated[i * 2 + 1] += midV;
                contributions[i]++;
            }
        }

        float[] result = new float[vertexCount * 2];
        for (int i = 0; i < vertexCount; i++) {
            float factor = contributions[i] > 0 ? contributions[i] : 1f;
            result[i * 2] = accumulated[i * 2] / factor;
            result[i * 2 + 1] = accumulated[i * 2 + 1] / factor;
        }
        return result;
    }
}
