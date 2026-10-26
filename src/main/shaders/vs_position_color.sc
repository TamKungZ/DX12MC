$input a_position, a_color0
$output v_color0, v_fog
#include "common.sh"

uniform mat4 u_mvp;
uniform mat4 u_fogModel;
uniform vec4 u_fogParams;
void main()
{
    vec4 clip = mul(u_mvp, vec4(a_position, 1.0));
    gl_Position = clip;
    v_fog = dx12mcFogDistance(mul(u_fogModel, clip).xyz, u_fogParams.z);
    v_color0 = a_color0;
}
