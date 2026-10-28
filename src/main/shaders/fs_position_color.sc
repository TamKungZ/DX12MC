$input v_color0, v_fog
#include "common.sh"

uniform vec4 u_color;
uniform vec4 u_fogParams;
uniform vec4 u_fogColor;
void main()
{
    dx12mcOutput(dx12mcApplyFog(v_color0 * u_color, v_fog, u_fogParams, u_fogColor));
}
