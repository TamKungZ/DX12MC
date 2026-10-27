$input v_texcoord0
#include "post.sh"

void main()
{
    vec2 oneTexel = vec2_splat(1.0) / u_postSize.xy;
    vec4 center = postSample(s_post0, v_texcoord0);
    vec4 left = postSample(s_post0, v_texcoord0 - vec2(oneTexel.x, 0.0));
    vec4 right = postSample(s_post0, v_texcoord0 + vec2(oneTexel.x, 0.0));
    vec4 up = postSample(s_post0, v_texcoord0 - vec2(0.0, oneTexel.y));
    vec4 down = postSample(s_post0, v_texcoord0 + vec2(0.0, oneTexel.y));
    float leftDiff = abs(center.a - left.a);
    float rightDiff = abs(center.a - right.a);
    float upDiff = abs(center.a - up.a);
    float downDiff = abs(center.a - down.a);
    float total = clamp(leftDiff + rightDiff + upDiff + downDiff, 0.0, 1.0);
    vec3 outColor = center.rgb * center.a + left.rgb * left.a + right.rgb * right.a + up.rgb * up.a + down.rgb * down.a;
    gl_FragColor = vec4(outColor * 0.2, total);
}
