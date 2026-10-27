$input v_texcoord0
#include "post.sh"

void main()
{
    vec2 sampleStep = u_postA.xy / u_postSize.xy;
    float actualRadius = round(u_postA.z * u_postA.w);
    vec4 blurred = vec4_splat(0.0);
    for (float a = -actualRadius + 0.5; a <= actualRadius; a += 2.0)
    {
        blurred += postSample(s_post0, v_texcoord0 + sampleStep * a);
    }
    blurred += postSample(s_post0, v_texcoord0 + sampleStep * actualRadius) / 2.0;
    gl_FragColor = blurred / (actualRadius + 0.5);
}
