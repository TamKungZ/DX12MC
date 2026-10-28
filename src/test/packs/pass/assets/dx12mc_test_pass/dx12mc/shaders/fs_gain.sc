$input v_texcoord0
#include "dx12mc.sh"

SAMPLER2D(s_input, 0);
uniform vec4 u_gain;
void main()
{
    vec4 color = texture2D(s_input, dx12mcPassUv(v_texcoord0));
    gl_FragColor = vec4(color.rgb * u_gain.rgb * (u_targetSize.x > 0.0 ? 1.0 : 0.0), 1.0);
}
