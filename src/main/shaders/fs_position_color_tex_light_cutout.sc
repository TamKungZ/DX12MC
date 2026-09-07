$input v_color0, v_texcoord0, v_texcoord2
#include "texture.sh"

SAMPLER2D(s_texColor, 0);
SAMPLER2D(s_lightmap, 2);
uniform vec4 u_color;
void main()
{
    vec4 base = texture2D(s_texColor, v_texcoord0) * v_color0 * u_color;
    if (base.a < 0.1) discard;
    vec2 lightUv = (v_texcoord2 + vec2_splat(8.0)) / 256.0;
    vec4 light = texture2D(s_lightmap, lightUv);
    gl_FragColor = base * light;
}
