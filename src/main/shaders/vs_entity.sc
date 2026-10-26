$input a_position, a_color0, a_texcoord0, a_texcoord1, a_texcoord2, a_normal
$output v_color0, v_texcoord0, v_fog, v_overlay, v_light
#include "texture.sh"

SAMPLER2D(s_overlay, 1);
SAMPLER2D(s_lightmap, 2);
uniform mat4 u_mvp;
uniform mat4 u_texMatrix;
uniform mat4 u_fogModel;
uniform vec4 u_fogParams;
uniform vec4 u_lightParams;
uniform vec4 u_lightDir0;
uniform vec4 u_lightDir1;
void main()
{
    vec4 clip = mul(u_mvp, vec4(a_position, 1.0));
    gl_Position = clip;
    vec4 uv = mul(u_texMatrix, vec4(a_texcoord0, 0.0, 1.0));
    v_texcoord0 = uv.xy;
    v_fog = dx12mcFogDistance(mul(u_fogModel, clip).xyz, u_fogParams.z);
    vec3 normal = (a_normal.xyz * 255.0 - vec3_splat(128.0)) / 127.0;
    v_color0 = a_color0;
    if (u_lightParams.z > 0.5) v_color0 = dx12mcMixLight(u_lightDir0.xyz, u_lightDir1.xyz, normal, a_color0);
    v_light = vec4_splat(1.0);
    if (u_lightParams.x > 0.5) v_light = dx12mcTexture2DLevel(s_lightmap, dx12mcLightmapUv(dx12mcUnsigned16(a_texcoord2), u_lightParams.x), 0.0);
    v_overlay = vec4_splat(1.0);
    if (u_lightParams.y > 0.5) v_overlay = dx12mcTexture2DLevel(s_overlay, (a_texcoord1 + vec2_splat(0.5)) / 16.0, 0.0);
}
