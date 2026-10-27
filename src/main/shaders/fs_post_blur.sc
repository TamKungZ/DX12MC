$input v_texcoord0
#include "post.sh"

void main()
{
    vec2 oneTexel = vec2_splat(1.0) / u_postSize.xy;
    float radius = u_postA.z;
    vec4 blurred = vec4_splat(0.0);
    float totalAlpha = 0.0;
    for (float r = -radius; r <= radius; r += 1.0)
    {
        vec4 sampleValue = postSample(s_post0, v_texcoord0 + oneTexel * r * u_postA.xy);
        totalAlpha += sampleValue.a;
        blurred += sampleValue;
    }
    gl_FragColor = vec4(blurred.rgb / (radius * 2.0 + 1.0), totalAlpha);
}
