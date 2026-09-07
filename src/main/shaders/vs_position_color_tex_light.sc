$input a_position, a_color0, a_texcoord0, a_texcoord2
$output v_color0, v_texcoord0, v_texcoord2
#include "common.sh"

uniform mat4 u_mvp;
uniform mat4 u_texMatrix;
void main()
{
    gl_Position = mul(u_mvp, vec4(a_position, 1.0));
    vec4 uv = mul(u_texMatrix, vec4(a_texcoord0, 0.0, 1.0));
    v_texcoord0 = uv.xy;
    v_texcoord2 = a_texcoord2;
    v_color0 = a_color0;
}
