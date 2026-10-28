$input v_texcoord0
#include "dx12mc.sh"

SAMPLER2D(s_input, 0);
void main()
{
    gl_FragColor = texture2D(s_input, dx12mcPassUv(v_texcoord0));
}
