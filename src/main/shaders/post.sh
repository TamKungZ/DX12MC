#ifndef DX12MC_SHADER_POST_SH
#define DX12MC_SHADER_POST_SH
#include "texture.sh"

SAMPLER2D(s_post0, 0);
SAMPLER2D(s_post1, 1);
uniform vec4 u_postSize;
uniform vec4 u_postA;
uniform vec4 u_postB;
uniform vec4 u_postC;
uniform vec4 u_postD;
uniform vec4 u_postE;
uniform vec4 u_postF;

vec4 postSample(Dx12mcSampler2D _sampler, vec2 _glCoord)
{
    return dx12mcTexture2DLevel(_sampler, vec2(_glCoord.x, 1.0 - _glCoord.y), 0.0);
}
#endif
