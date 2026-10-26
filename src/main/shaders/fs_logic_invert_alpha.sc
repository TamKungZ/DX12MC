$input v_color0, v_fog
#include "common.sh"

uniform vec4 u_color;
uniform vec4 u_material;
void main()
{
    if (u_material.y > 0.5 && v_color0.a < u_material.x) discard;
    if (u_material.y < 0.5 && (v_color0 * u_color).a < u_material.x) discard;
    gl_FragColor = vec4_splat(1.0);
}
