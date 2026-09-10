#version 330 core

layout (location = 0) in ivec4 joint;
layout (location = 1) in vec4 weight;
layout (location = 2) in vec3 position;
layout (location = 3) in vec3 normal;
layout (location = 4) in vec4 tangent;

uniform samplerBuffer jointMatrices;

uniform samplerBuffer morphDeltasPosition;
uniform samplerBuffer morphDeltasNormal;
uniform float morphWeights[64];
uniform int activeMorphCount;
uniform int vertexCount;

// The entity's own placement this frame (PoseStack.Pose) — everything vanilla's own vertex
// pipeline bakes into vertex positions before the camera's ModelView/Proj apply, done here once
// per skinned vertex instead of again on the CPU after transform feedback.
uniform mat4 poseMatrix;
uniform mat3 poseNormalMatrix;

out vec3 outPosition;
out vec3 outNormal;
out vec4 outTangent;

// A weight-sum-of-~0 (a vertex with all-zero WEIGHTS_0 - unusual but real: an exporter that left a
// helper/decorative vertex unrigged, or a bone slot pruned by a rig retarget) makes skinMatrix the
// all-zero matrix below, which would otherwise produce a literal 0/0 NaN dividing skinnedPos by its
// own w, and inverse() of the resulting singular linearMatrix is separately undefined per the GLSL
// spec. GeometryUtils.sanitizeSkinning already prevents this at import time by rebinding such a
// vertex fully onto joint 0 - this is the cheap second line of defense for a weight sum that still
// rounds to exactly 0 in fp32 despite that, or for any other unforeseen degenerate input, the same
// "belt and suspenders" relationship GeometryUtils.orthogonalizeOrFallback has with sanitizeTangents.
vec3 safeNormalize(vec3 v, vec3 fallback) {
    float lenSq = dot(v, v);
    return lenSq > 1.0e-12 ? v * inversesqrt(lenSq) : fallback;
}

void main() {
    vec3 morphedPos = position;
    vec3 morphedNor = normal;

    int vertId = gl_VertexID;

    for (int i = 0; i < activeMorphCount; i++) {
        float w = morphWeights[i];
        if (abs(w) > 0.0001) {
            int bufferIndex = (i * vertexCount) + vertId;
            morphedPos += texelFetch(morphDeltasPosition, bufferIndex).xyz * w;
            morphedNor += texelFetch(morphDeltasNormal, bufferIndex).xyz * w;
        }
    }

    int jx = joint.x * 4;
    int jy = joint.y * 4;
    int jz = joint.z * 4;
    int jw = joint.w * 4;

    mat4 skinMatrix = weight.x * mat4(
        texelFetch(jointMatrices, jx), texelFetch(jointMatrices, jx + 1),
        texelFetch(jointMatrices, jx + 2), texelFetch(jointMatrices, jx + 3)
    ) + weight.y * mat4(
        texelFetch(jointMatrices, jy), texelFetch(jointMatrices, jy + 1),
        texelFetch(jointMatrices, jy + 2), texelFetch(jointMatrices, jy + 3)
    ) + weight.z * mat4(
        texelFetch(jointMatrices, jz), texelFetch(jointMatrices, jz + 1),
        texelFetch(jointMatrices, jz + 2), texelFetch(jointMatrices, jz + 3)
    ) + weight.w * mat4(
        texelFetch(jointMatrices, jw), texelFetch(jointMatrices, jw + 1),
        texelFetch(jointMatrices, jw + 2), texelFetch(jointMatrices, jw + 3)
    );

    float weightSum = weight.x + weight.y + weight.z + weight.w;
    vec3 skinnedPosition;
    mat3 linearMatrix;
    if (weightSum > 1.0e-6) {
        vec4 skinnedPos = skinMatrix * vec4(morphedPos, 1.0);
        skinnedPosition = skinnedPos.xyz / skinnedPos.w;
        linearMatrix = mat3(skinMatrix);
    } else {
        // No usable joint weight at all - falls back to the bind pose rather than a 0/0 NaN. See
        // this file's own note above on why this shouldn't normally be reachable in practice.
        skinnedPosition = morphedPos;
        linearMatrix = mat3(1.0);
    }

    // Tangent is an ordinary direction vector embedded in the surface, not a covector like the
    // normal - it transforms by the skin matrix's own linear part directly, never its inverse-
    // transpose (that correction exists specifically to keep normals perpendicular under non-
    // uniform scale; applying it to a tangent would be wrong). inverse() of a singular linearMatrix
    // (e.g. from an out-of-range JOINTS_0 index reading a zero/garbage texel even with a nonzero
    // weight sum) is undefined per the GLSL spec, so it's only taken when linearMatrix is genuinely
    // invertible; the identity fallback keeps both the normal and tangent finite either way.
    float det = determinant(linearMatrix);
    mat3 normalMatrix = abs(det) > 1.0e-8 ? transpose(inverse(linearMatrix)) : mat3(1.0);
    vec3 skinnedNormal = safeNormalize(normalMatrix * morphedNor, vec3(0.0, 1.0, 0.0));
    vec3 skinnedTangent = safeNormalize(linearMatrix * tangent.xyz, vec3(1.0, 0.0, 0.0));

    outPosition = (poseMatrix * vec4(skinnedPosition, 1.0)).xyz;
    outNormal = normalize(poseNormalMatrix * skinnedNormal);
    // tangent.w (handedness sign) passes through unchanged - it only ever needs to be +-1 and is
    // invariant under the rotation/uniform-scale transforms this pipeline applies.
    outTangent = vec4(normalize(poseNormalMatrix * skinnedTangent), tangent.w);
}
