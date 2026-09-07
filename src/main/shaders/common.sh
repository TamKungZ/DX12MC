#ifndef DX12MC_SHADER_COMMON_SH
#define DX12MC_SHADER_COMMON_SH

vec2 vec2_splat(float _x) { return vec2(_x, _x); }
vec3 vec3_splat(float _x) { return vec3(_x, _x, _x); }
vec4 vec4_splat(float _x) { return vec4(_x, _x, _x, _x); }
#endif
