$input a_position
$output v_texcoord0
#include "common.sh"

void main()
{
    gl_Position = vec4(a_position.xy, 0.6, 1.0);
    v_texcoord0 = a_position.xy * 0.5 + vec2_splat(0.5);
}
