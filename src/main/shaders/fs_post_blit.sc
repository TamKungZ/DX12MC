$input v_texcoord0
#include "post.sh"

void main()
{
    gl_FragColor = postSample(s_post0, v_texcoord0) * u_postA;
}
