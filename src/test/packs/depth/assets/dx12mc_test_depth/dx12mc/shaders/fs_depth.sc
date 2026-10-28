$input v_texcoord0
#include "dx12mc.sh"

SAMPLER2D(s_depth, 0);
void main()
{
    float depth = texture2D(s_depth, dx12mcPassUv(v_texcoord0)).r;
    gl_FragColor = vec4(depth, depth, depth, 1.0);
}
