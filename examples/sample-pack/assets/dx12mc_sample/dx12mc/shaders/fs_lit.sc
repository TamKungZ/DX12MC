$input v_color0, v_texcoord0, v_fog, v_position
#include "dx12mc.sh"

SAMPLER2D(s_texColor, 0);
SAMPLER2D(s_normals, 3);
SAMPLER2D(s_specular, 4);
uniform vec4 u_light;

void main()
{
    vec4 base = dx12mcTexture2DMaxLod(s_texColor, v_texcoord0, u_texLod.x);
    vec4 color = base * v_color0 * u_color;

    // Block vertices have no normals: the face normal and the tangent frame come from
    // screen-space derivatives of the camera-relative position and the atlas UV.
    vec3 dpx = ddx(v_position);
    vec3 dpy = ddy(v_position);
    vec3 face = normalize(cross(dpx, dpy));
    if (dot(face, v_position) > 0.0) face = -face;
    vec2 duvx = ddx(v_texcoord0);
    vec2 duvy = ddy(v_texcoord0);
    vec3 dpyPerp = cross(dpy, face);
    vec3 dpxPerp = cross(face, dpx);
    vec3 tangent = dpyPerp * duvx.x + dpxPerp * duvy.x;
    vec3 bitangent = dpyPerp * duvx.y + dpxPerp * duvy.y;
    float scale = rsqrt(max(max(dot(tangent, tangent), dot(bitangent, bitangent)), 1e-20));

    // labPBR 1.3: RG is the tangent-space normal (DirectX, Y-), B is ambient occlusion.
    vec4 packedNormal = dx12mcTexture2DMaxLod(s_normals, v_texcoord0, u_texLod.x);
    vec2 xy = packedNormal.rg * 2.0 - vec2_splat(1.0);
    float z = sqrt(saturate(1.0 - dot(xy, xy)));
    vec3 normal = normalize(tangent * scale * xy.x + bitangent * scale * xy.y + face * z);

    // labPBR 1.3: R is perceptual smoothness.
    float smoothness = dx12mcTexture2DMaxLod(s_specular, v_texcoord0, u_texLod.x).r;
    float roughness = (1.0 - smoothness) * (1.0 - smoothness);

    vec3 light = normalize(u_light.xyz);
    vec3 view = normalize(-v_position);
    vec3 halfway = normalize(light + view);
    float diffuse = max(dot(normal, light), 0.0) * packedNormal.b;
    float power = 2.0 / max(roughness * roughness, 0.002) - 2.0;
    float highlight = pow(max(dot(normal, halfway), 0.0), power) * (1.0 - roughness) * u_light.w;
    color.rgb = color.rgb * (0.45 + 0.55 * diffuse) + vec3_splat(highlight);
    gl_FragColor = dx12mcApplyFog(color, v_fog, u_fogParams, u_fogColor);
}
