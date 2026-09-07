$input v_color0, v_texcoord0
#include "texture.sh"

SAMPLER2D(s_texColor, 0);
uniform vec4 u_color;
void main()
{
    gl_FragColor = texture2D(s_texColor, v_texcoord0) * v_color0 * u_color;
}
