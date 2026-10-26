$input v_color0, v_texcoord0, v_fog
#include "texture.sh"

SAMPLER2D(s_texColor, 0);
uniform vec4 u_color;
uniform vec4 u_fogParams;
uniform vec4 u_fogColor;
uniform vec4 u_material;
uniform vec4 u_texLod;
void main()
{
    vec4 base = dx12mcTexture2DMaxLod(s_texColor, v_texcoord0, u_texLod.x);
    if (u_material.w > 0.5) base = base.rrrr;

    gl_FragColor = dx12mcApplyFog(base * v_color0 * u_color, v_fog, u_fogParams, u_fogColor);
}
