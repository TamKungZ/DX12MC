#ifndef DX12MC_SHADER_COMMON_SH
#define DX12MC_SHADER_COMMON_SH

vec2 vec2_splat(float _x) { return vec2(_x, _x); }
vec3 vec3_splat(float _x) { return vec3(_x, _x, _x); }
vec4 vec4_splat(float _x) { return vec4(_x, _x, _x, _x); }

float dx12mcFogDistance(vec3 _pos, mat4 _view, float _shape)
{
    if (_shape < 0.5) return length(mul(_view, vec4(_pos, 1.0)).xyz);
    if (_shape < 1.5)
    {
        float distXZ = length(mul(_view, vec4(_pos.x, 0.0, _pos.z, 1.0)).xyz);
        float distY = length(mul(_view, vec4(0.0, _pos.y, 0.0, 1.0)).xyz);
        return max(distXZ, distY);
    }
    return -mul(_view, vec4(_pos, 1.0)).z;
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

vec4 dx12mcMixLight(vec3 _lightDir0, vec3 _lightDir1, vec3 _normal, vec4 _color)
{
    float light0 = max(0.0, dot(_lightDir0, _normal));
    float light1 = max(0.0, dot(_lightDir1, _normal));
    float lightAccum = min(1.0, (light0 + light1) * 0.6 + 0.4);
    return vec4(_color.rgb * lightAccum, _color.a);
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
#endif
