$input v_color0, v_texcoord0, v_fog, v_position
#include "dx12mc.sh"

SAMPLER2D(s_normals, 3);
SAMPLER2D(s_specular, 4);
void main()
{
    vec4 normal = dx12mcTexture2DMaxLod(s_normals, v_texcoord0, u_texLod.x);
    vec4 specular = dx12mcTexture2DMaxLod(s_specular, v_texcoord0, u_texLod.x);
    gl_FragColor = vec4(normal.rg, specular.r, 1.0);
}
