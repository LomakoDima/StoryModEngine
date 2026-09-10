#version 150

#moj_import <fog.glsl>

uniform sampler2D Sampler0;
// Bound by PbrUniformBinder whenever the material has (or defaults to) a LabPBR normal/specular
// map — see that class's own doc for the fixed-texture-unit self-assignment this shader relies on,
// since no shader pack ever patches this file. Declaring them here is what makes those binds do
// anything at all; a material with no authored PBR maps still gets the 1x1 flat-normal/default-
// specular fallback textures, which decode to occlusion=1/smoothness=0/dielectric-F0 — the fallback
// lighting below reduces to "no highlight, full diffuse" for exactly that case, so this is additive
// for existing content, not a behavior change.
uniform sampler2D normals;
uniform sampler2D specular;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
// Set per-draw by PbrUniformBinder from the primitive's own MaterialData: the material's real
// alphaCutoff under MASK, 0.0 (never discards) under BLEND, 0.1 (this engine's long-standing default)
// under OPAQUE. See PbrUniformBinder's own doc.
uniform float AlphaCutoff;
// The same two fixed vanilla "sun" directions minecraft_mix_light already used to light
// vertexColor — reused here for a per-pixel specular term so the fallback highlight stays
// consistent with whatever already drives vanilla's own flat entity lighting.
uniform vec3 Light0_Direction;
uniform vec3 Light1_Direction;

in float vertexDistance;
in vec4 vertexColor;
in vec4 lightMapColor;
in vec4 overlayColor;
in vec2 texCoord0;
in vec3 vNormal;
in vec3 vViewDir;

out vec4 fragColor;

/**
 * A cheap Blinn-Phong stand-in for a real BRDF — not tangent-space normal mapping (vNormal is the
 * flat, interpolated per-vertex normal; the "normals" map's own XY channels are never read here,
 * only its blue occlusion channel), but enough that a PBR-authored material actually looks
 * different from a flat one when no shader pack is installed to do the real thing. See the mod's
 * own PBR audit for why this exists at all: without it, this data reached the GPU and had zero
 * visible effect.
 */
void main() {
    vec4 albedo = texture(Sampler0, texCoord0);
    if (albedo.a < AlphaCutoff) {
        discard;
    }

    vec4 normalSample = texture(normals, texCoord0);
    vec4 specSample = texture(specular, texCoord0);
    float occlusion = normalSample.b;
    float smoothness = specSample.r;
    // LabPBR's own convention (see this mod's LabPbrConverter): G >= 230/255 means "metal, use
    // albedo as F0"; below that, G directly encodes a dielectric F0 fraction (~0.02-0.05), not a
    // metalness percentage — reading it as a continuous metalness value would be wrong.
    bool isMetal = specSample.g >= (230.0 / 255.0);
    float metalness = isMetal ? 1.0 : 0.0;
    float emission = specSample.a;

    vec3 tintedAlbedo = albedo.rgb * vertexColor.rgb * ColorModulator.rgb;
    // Metals have ~no true Lambertian diffuse response — but with no environment/IBL reflection
    // term here (there's no cheap way to fake one against Minecraft's own blocky world), zeroing
    // this out entirely for metal leaves nothing but two tiny, near-pinpoint specular highlights
    // (see shininess below) on an otherwise fully black sphere — confirmed by testing this exact
    // shader without a shader pack: a smooth metal ball rendered solid black except for one small
    // bright patch. METAL_AMBIENT_FLOOR keeps a dim, albedo-tinted base everywhere as a stand-in
    // for that missing environment term, so a metal reads as "dark and shiny," not "invisible
    // except where directly lit." Ambient occlusion still darkens this term either way.
    const float METAL_AMBIENT_FLOOR = 0.35;
    vec3 diffuse = tintedAlbedo * mix(1.0, METAL_AMBIENT_FLOOR, metalness) * occlusion;

    vec3 N = normalize(vNormal);
    vec3 V = normalize(vViewDir);
    vec3 specularTint = isMetal ? albedo.rgb : vec3(specSample.g);
    float shininess = mix(4.0, 300.0, smoothness);
    float specStrength = mix(0.1, 1.0, smoothness);
    float spec0 = pow(max(dot(N, normalize(Light0_Direction + V)), 0.0), shininess);
    float spec1 = pow(max(dot(N, normalize(Light1_Direction + V)), 0.0), shininess);
    vec3 specularColor = specularTint * (spec0 + spec1) * specStrength;

    vec3 emissiveColor = albedo.rgb * emission;

    vec4 color = vec4(diffuse + specularColor + emissiveColor, albedo.a * vertexColor.a * ColorModulator.a);
    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
    color *= lightMapColor;
    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
