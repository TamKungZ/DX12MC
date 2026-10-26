$input a_position, a_color0, a_texcoord0, a_normal
$output v_color0, v_fog
#include "common.sh"

uniform mat4 u_mvp;
uniform mat4 u_fogModel;
uniform vec4 u_fogParams;
uniform vec4 u_lineParams;
void main()
{
    vec3 direction = (a_normal.xyz * 255.0 - vec3_splat(128.0)) / 127.0;
    vec4 clip = mul(u_mvp, vec4(a_position, 1.0));
    vec4 lineEnd = mul(u_mvp, vec4(a_position + direction, 1.0));
    vec3 ndc1 = clip.xyz / clip.w;
    vec3 ndc2 = lineEnd.xyz / lineEnd.w;
    vec2 screenDirection = normalize((ndc2.xy - ndc1.xy) * u_lineParams.yz);
    vec2 lineOffset = vec2(-screenDirection.y, screenDirection.x) * u_lineParams.x / u_lineParams.yz;
    if (lineOffset.x < 0.0) lineOffset *= -1.0;
    gl_Position = vec4((ndc1 + vec3(lineOffset * a_texcoord0.x, 0.0)) * clip.w, clip.w);
    v_fog = dx12mcFogDistance(mul(u_fogModel, clip).xyz, u_fogParams.z);
    v_color0 = a_color0;
}
