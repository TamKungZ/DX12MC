#ifndef DX12MC_PACK_SH
#define DX12MC_PACK_SH

#ifdef DX12MC_DEFINE_SPLAT
vec2 vec2_splat(float _x) { return vec2(_x, _x); }
vec3 vec3_splat(float _x) { return vec3(_x, _x, _x); }
vec4 vec4_splat(float _x) { return vec4(_x, _x, _x, _x); }
#endif

#define DX12MC_REGISTER(_type, _reg) register(_type ## _reg)

struct Dx12mcSampler2D
{
    SamplerState m_sampler;
    Texture2D m_texture;
};

#define SAMPLER2D(_name, _reg) \
    uniform SamplerState _name ## Sampler : DX12MC_REGISTER(s, _reg); \
    uniform Texture2D _name ## Texture : DX12MC_REGISTER(t, _reg); \
    static Dx12mcSampler2D _name = { _name ## Sampler, _name ## Texture }

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
#define texture2D(_sampler, _coord) dx12mcTexture2D(_sampler, _coord)

vec2 dx12mcPassUv(vec2 _glCoord)
{
    return vec2(_glCoord.x, 1.0 - _glCoord.y);
}

float dx12mcFogDistance(vec3 _pos, float _shape)
{
    if (_shape < 0.5) return length(_pos);
    if (_shape < 1.5) return max(length(_pos.xz), abs(_pos.y));
    return -_pos.z;
}
vec4 dx12mcApplyFog(vec4 _color, float _distance, vec4 _params, vec4 _fogColor)
{
    if (_params.w < 0.5) return _color;
    if (_params.w < 1.5)
    {
        if (_distance <= _params.x) return _color;
        float fogValue = _distance < _params.y ? smoothstep(_params.x, _params.y, _distance) : 1.0;
        return vec4(lerp(_color.rgb, _fogColor.rgb, fogValue * _fogColor.a), _color.a);
    }
    float fade = 1.0;
    if (_distance >= _params.y) fade = 0.0;
    if (_distance > _params.x && _distance < _params.y) fade = smoothstep(_params.y, _params.x, _distance);
    if (_params.w < 2.5) return _color * fade;
    return vec4(_color.rgb * fade, _color.a);
}
vec2 dx12mcUnsigned16(ivec2 _value)
{
    return vec2(_value & ivec2(0xFFFF, 0xFFFF));
}
vec2 dx12mcLightmapUv(vec2 _uv2, float _mode)
{
    if (_mode < 1.5) return clamp(_uv2 / 256.0, vec2_splat(0.5 / 16.0), vec2_splat(15.5 / 16.0));
    return (floor(_uv2 / 16.0) + vec2_splat(0.5)) / 16.0;
}

uniform mat4 u_mvp;
uniform vec4 u_color;
uniform mat4 u_texMatrix;
uniform mat4 u_fogModel;
uniform vec4 u_fogParams;
uniform vec4 u_fogColor;
uniform vec4 u_material;
uniform vec4 u_texLod;
uniform vec4 u_lightParams;
uniform vec4 u_lightDir0;
uniform vec4 u_lightDir1;
uniform mat4 u_cameraViewProj;
uniform mat4 u_cameraInvViewProj;
uniform mat4 u_cameraPrevViewProj;
uniform mat4 u_cameraView;
uniform mat4 u_cameraProj;
uniform mat4 u_cameraInvProj;
uniform vec4 u_cameraPos;
uniform vec4 u_sunDir;
uniform vec4 u_moonDir;
uniform vec4 u_time;
uniform vec4 u_viewSize;
uniform vec4 u_frameFog;
uniform vec4 u_frameFogColor;
uniform vec4 u_targetSize;

#define DX12MC_SLOT_TEXCOLOR 0
#define DX12MC_SLOT_OVERLAY 1
#define DX12MC_SLOT_LIGHTMAP 2
#define DX12MC_SLOT_NORMALS 3
#define DX12MC_SLOT_SPECULAR 4

#endif
