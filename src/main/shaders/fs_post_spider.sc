$input v_texcoord0
#include "post.sh"

void main()
{
    float inRadians = u_postB.x * 0.0174532925;
    float cosine = cos(inRadians);
    float sine = sin(inRadians);
    float rotU = v_texcoord0.x * cosine - v_texcoord0.y * sine;
    float rotV = v_texcoord0.y * cosine + v_texcoord0.x * sine;
    vec2 scaledCoord = vec2(rotU, rotV) * u_postA.xy + u_postA.zw;
    vec4 scaledTexel = postSample(s_post0, scaledCoord);
    vec4 blurTexel = postSample(s_post1, v_texcoord0);
    vec4 outTexel = scaledTexel;
    if (scaledCoord.x < u_postC.x) outTexel = blurTexel;
    if (scaledCoord.y < u_postC.y) outTexel = blurTexel;
    if (scaledCoord.x > u_postC.z) outTexel = blurTexel;
    if (scaledCoord.y > u_postC.w) outTexel = blurTexel;
    if (scaledCoord.x < u_postD.x) outTexel = lerp(blurTexel, outTexel, (u_postC.x - scaledCoord.x) / (u_postC.x - u_postD.x));
    if (scaledCoord.y < u_postD.y) outTexel = lerp(blurTexel, outTexel, (u_postC.y - scaledCoord.y) / (u_postC.y - u_postD.y));
    if (scaledCoord.x > u_postD.z) outTexel = lerp(blurTexel, outTexel, (u_postC.z - scaledCoord.x) / (u_postC.z - u_postD.z));
    if (scaledCoord.y > u_postD.w) outTexel = lerp(blurTexel, outTexel, (u_postC.w - scaledCoord.y) / (u_postC.w - u_postD.w));
    gl_FragColor = vec4(outTexel.rgb, 1.0);
}
