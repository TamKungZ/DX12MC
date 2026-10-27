$input a_position
$output v_texProj
#include "common.sh"

uniform mat4 u_mvp;
void main()
{
    vec4 clip = mul(u_mvp, vec4(a_position, 1.0));
    gl_Position = clip;
    v_texProj = vec4(clip.xy * 0.5 + vec2_splat(clip.w * 0.5), clip.zw);
}
