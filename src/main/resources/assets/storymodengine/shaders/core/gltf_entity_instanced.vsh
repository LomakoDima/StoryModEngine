#version 150
#extension GL_ARB_explicit_attrib_location : enable

#moj_import <light.glsl>
#moj_import <fog.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in vec3 Normal;

// Per-instance (glVertexAttribDivisor 1) — one of these per entity sharing this draw call, not per
// vertex. Explicit locations: ShaderInstance auto-binds Position/Color/UV0/Normal (and every other
// NEW_ENTITY element, referenced here or not) to 0-6 by iterating the vertex format's own element
// list — these start at 7 to stay clear of that range. A mat4 attribute occupies 4 consecutive
// locations and a mat3 occupies 3 (one per column), so this whole block runs 7..15 — deliberately
// kept inside 0..15, the GL-guaranteed minimum for GL_MAX_VERTEX_ATTRIBS. The first version started
// at 8 and ran to 16, one past that guaranteed range; linking failed silently (ProgramManager logs a
// warning but never throws) and every instanced draw ran through an unlinked program.
layout(location = 7) in mat4 InstanceModelView;
layout(location = 11) in mat3 InstanceNormalMatrix;
layout(location = 14) in ivec2 InstanceOverlay;
layout(location = 15) in ivec2 InstanceLight;

uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform int FogShape;

uniform vec3 Light0_Direction;
uniform vec3 Light1_Direction;

out float vertexDistance;
out vec4 vertexColor;
out vec4 lightMapColor;
out vec4 overlayColor;
out vec2 texCoord0;
out vec3 vNormal;
out vec3 vViewDir;

void main() {
    // The entity's own placement (equivalent to what the CPU path bakes into vertex positions
    // directly) happens here per-instance; ModelViewMat below is only the camera's own view, shared
    // by every instance in this one draw call — same split HollowEngine's instanced shader uses.
    vec4 worldPosition = InstanceModelView * vec4(Position, 1.0);
    vec3 viewPosition = (ModelViewMat * worldPosition).xyz;

    gl_Position = ProjMat * vec4(viewPosition, 1.0);
    // fog_distance(mat4, vec3, int) expects to do its own modelView multiply (see fog.glsl); passing
    // identity here is correct since viewPosition is already fully transformed to view space above.
    vertexDistance = fog_distance(mat4(1.0), viewPosition, FogShape);

    vec3 worldNormal = normalize(InstanceNormalMatrix * Normal);
    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, worldNormal, Color);
    lightMapColor = texelFetch(Sampler2, InstanceLight / 16, 0);
    overlayColor = texelFetch(Sampler1, InstanceOverlay, 0);
    texCoord0 = UV0;
    // For the fallback specular term in the fragment shader — no shader pack ever patches this
    // file (see PbrUniformBinder's own doc), so this is the only PBR-response lighting a material
    // rendered through this path ever gets. The camera sits at the origin of this same per-instance
    // -transformed space (ModelViewMat above is only the camera's own rotation/projection, applied
    // after this), so "toward the camera" is simply the negated, normalized worldPosition.
    vNormal = worldNormal;
    vViewDir = normalize(-worldPosition.xyz);
}
