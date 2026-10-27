$input v_texcoord0
#include "post.sh"

void main()
{
    vec2 mosaicInSize = u_postSize.xy / u_postA.z;
    vec2 fractPix = frac(v_texcoord0 * mosaicInSize) / mosaicInSize;
    vec4 baseTexel = postSample(s_post0, v_texcoord0 - fractPix);
    vec3 fractTexel = baseTexel.rgb - frac(baseTexel.rgb * u_postA.x) / u_postA.x;
    float luma = dot(fractTexel, vec3(0.3, 0.59, 0.11));
    vec3 chroma = (fractTexel - vec3_splat(luma)) * u_postA.y;
    baseTexel.rgb = vec3_splat(luma) + chroma;
    baseTexel.a = 1.0;
    gl_FragColor = baseTexel;
}
