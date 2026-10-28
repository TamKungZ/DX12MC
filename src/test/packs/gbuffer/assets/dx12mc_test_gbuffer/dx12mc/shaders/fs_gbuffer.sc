$input v_color0, v_texcoord0, v_fog, v_position
#include "dx12mc.sh"

SAMPLER2D(s_texColor, 0);
void main()
{
    vec4 base = dx12mcTexture2DMaxLod(s_texColor, v_texcoord0, u_texLod.x);
    gl_FragData[0] = base * v_color0 * u_color;
    gl_FragData[1] = vec4(0.25, 0.5, 0.75, 1.0);
}
