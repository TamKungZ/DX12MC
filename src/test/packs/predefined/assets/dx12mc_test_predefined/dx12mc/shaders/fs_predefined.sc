$input v_texcoord0
#include "dx12mc.sh"

uniform mat4 u_viewProj;
void main()
{
    gl_FragColor = mul(u_viewProj, vec4(v_texcoord0, 0.0, 1.0));
}
