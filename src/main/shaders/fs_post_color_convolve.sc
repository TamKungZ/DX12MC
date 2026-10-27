$input v_texcoord0
#include "post.sh"

void main()
{
    vec4 inTexel = postSample(s_post0, v_texcoord0);
    float redValue = dot(inTexel.rgb, u_postB.xyz);
    float greenValue = dot(inTexel.rgb, u_postC.xyz);
    float blueValue = dot(inTexel.rgb, u_postD.xyz);
    vec3 outColor = vec3(redValue, greenValue, blueValue);
    outColor = (outColor * u_postF.xyz) + u_postE.xyz;
    float luma = dot(outColor, u_postA.xyz);
    vec3 chroma = outColor - vec3_splat(luma);
    outColor = (chroma * u_postA.w) + vec3_splat(luma);
    gl_FragColor = vec4(outColor, 1.0);
}
