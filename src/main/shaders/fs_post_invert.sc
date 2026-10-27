$input v_texcoord0
#include "post.sh"

void main()
{
    vec4 diffuseColor = postSample(s_post0, v_texcoord0);
    vec4 invertColor = vec4_splat(1.0) - diffuseColor;
    vec4 outColor = lerp(diffuseColor, invertColor, u_postA.x);
    gl_FragColor = vec4(outColor.rgb, 1.0);
}
