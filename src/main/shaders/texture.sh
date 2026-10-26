#ifndef DX12MC_SHADER_TEXTURE_SH
#define DX12MC_SHADER_TEXTURE_SH
#include "common.sh"
#define DX12MC_REGISTER(_type, _reg) register(_type ## _reg)

struct Dx12mcSampler2D
{
    SamplerState m_sampler;
    Texture2D m_texture;
};
vec4 dx12mcTexture2D(Dx12mcSampler2D _sampler, vec2 _coord)
{
    return _sampler.m_texture.Sample(_sampler.m_sampler, _coord);
}
vec4 dx12mcTexture2DMaxLod(Dx12mcSampler2D _sampler, vec2 _coord, float _maxLod)
{
    float lod = _sampler.m_texture.CalculateLevelOfDetailUnclamped(_sampler.m_sampler, _coord);
    return _sampler.m_texture.SampleLevel(_sampler.m_sampler, _coord, min(lod, _maxLod));
}
vec4 dx12mcTexture2DLevel(Dx12mcSampler2D _sampler, vec2 _coord, float _level)
{
    return _sampler.m_texture.SampleLevel(_sampler.m_sampler, _coord, _level);
}

#define SAMPLER2D(_name, _reg) \
    uniform SamplerState _name ## Sampler : DX12MC_REGISTER(s, _reg); \
    uniform Texture2D _name ## Texture : DX12MC_REGISTER(t, _reg); \
    static Dx12mcSampler2D _name = { _name ## Sampler, _name ## Texture }
#define texture2D(_sampler, _coord) dx12mcTexture2D(_sampler, _coord)
#endif
