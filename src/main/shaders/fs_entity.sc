$input v_color0, v_texcoord0, v_fog, v_overlay, v_light
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

    vec4 color = base * v_color0 * u_color;
    color.rgb = lerp(v_overlay.rgb, color.rgb, v_overlay.a);
    gl_FragColor = dx12mcApplyFog(color * v_light, v_fog, u_fogParams, u_fogColor);
}
