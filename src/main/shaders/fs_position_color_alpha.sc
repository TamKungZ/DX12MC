$input v_color0, v_fog
#include "common.sh"

uniform vec4 u_color;
uniform vec4 u_fogParams;
uniform vec4 u_fogColor;
uniform vec4 u_material;
void main()
{
    if (u_material.y > 0.5 && v_color0.a < u_material.x) discard;
    vec4 color = v_color0 * u_color;
    if (u_material.y < 0.5 && color.a < u_material.x) discard;
    gl_FragColor = dx12mcApplyFog(color, v_fog, u_fogParams, u_fogColor);
}
