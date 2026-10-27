$input v_texProj
#include "texture.sh"

SAMPLER2D(s_texColor, 0);
SAMPLER2D(s_overlay, 1);
uniform vec4 u_portal;

static const vec3 COLORS[16] = {
    vec3(0.022087, 0.098399, 0.110818),
    vec3(0.011892, 0.095924, 0.089485),
    vec3(0.027636, 0.101689, 0.100326),
    vec3(0.046564, 0.109883, 0.114838),
    vec3(0.064901, 0.117696, 0.097189),
    vec3(0.063761, 0.086895, 0.123646),
    vec3(0.084817, 0.111994, 0.166380),
    vec3(0.097489, 0.154120, 0.091064),
    vec3(0.106152, 0.131144, 0.195191),
    vec3(0.097721, 0.110188, 0.187229),
    vec3(0.133516, 0.138278, 0.148582),
    vec3(0.070006, 0.243332, 0.235792),
    vec3(0.196766, 0.142899, 0.214696),
    vec3(0.047281, 0.315338, 0.321970),
    vec3(0.204675, 0.390010, 0.302066),
    vec3(0.080955, 0.314821, 0.661491)
};

void main()
{
    vec4 p = v_texProj;
    vec3 color = dx12mcTexture2DLevel(s_texColor, p.xy / p.w, 0.0).rgb * COLORS[0];
    int layers = int(u_portal.y);
    for (int i = 0; i < layers; i++)
    {
        float layer = float(i + 1);
        float degrees = fmod((layer * layer * 4321.0 + layer * 9.0) * 2.0, 360.0);
        float angle = radians(degrees);
        float c = cos(angle);
        float s = sin(angle);
        float scale = (4.5 - layer / 4.0) * 2.0;
        vec2 xy = scale * vec2(c * p.x - s * p.y, s * p.x + c * p.y);
        xy.x += 17.0 / layer * p.w;
        xy.y += (2.0 + layer / 1.5) * (u_portal.x * 1.5) * p.w;
        xy = xy * 0.5 + vec2_splat(0.25 * p.w);
        color += dx12mcTexture2DLevel(s_overlay, xy / p.w, 0.0).rgb * COLORS[i];
    }
    gl_FragColor = vec4(color, 1.0);
}
