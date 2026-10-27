$input v_texcoord0
#include "post.sh"

void main()
{
    vec2 sampleStep = u_postA.xy / u_postSize.xy;
    vec4 blurred = vec4_splat(0.0);
    float radius = 2.0;
    for (float a = -radius + 0.5; a <= radius; a += 2.0)
    {
        blurred += postSample(s_post0, v_texcoord0 + sampleStep * a);
    }
    blurred += postSample(s_post0, v_texcoord0 + sampleStep * radius) / 2.0;
    gl_FragColor = vec4((blurred / (radius + 0.5)).rgb, blurred.a);
}
