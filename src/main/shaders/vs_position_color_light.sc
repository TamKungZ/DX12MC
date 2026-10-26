$input a_position, a_color0, a_texcoord2
$output v_color0, v_fog
#include "texture.sh"

SAMPLER2D(s_lightmap, 2);
uniform mat4 u_mvp;
uniform mat4 u_fogModel;
uniform vec4 u_fogParams;
uniform vec4 u_lightParams;
void main()
{
    vec4 clip = mul(u_mvp, vec4(a_position, 1.0));
    gl_Position = clip;
    v_fog = dx12mcFogDistance(mul(u_fogModel, clip).xyz, u_fogParams.z);
    vec2 lightUv = dx12mcLightmapUv(dx12mcUnsigned16(a_texcoord2), u_lightParams.x);
    v_color0 = a_color0 * dx12mcTexture2DLevel(s_lightmap, lightUv, 0.0);
}
