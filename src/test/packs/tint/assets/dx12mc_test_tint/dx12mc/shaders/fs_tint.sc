$input v_color0, v_texcoord0, v_fog, v_position
#include "dx12mc.sh"

SAMPLER2D(s_texColor, 0);
uniform vec4 u_tint;
void main()
{
    vec4 base = dx12mcTexture2DMaxLod(s_texColor, v_texcoord0, u_texLod.x);
    vec4 color = base * v_color0 * u_color;
    if (color.a < u_material.x) discard;
    color.rgb *= u_tint.rgb;
    gl_FragColor = dx12mcApplyFog(color, v_fog, u_fogParams, u_fogColor);
}
