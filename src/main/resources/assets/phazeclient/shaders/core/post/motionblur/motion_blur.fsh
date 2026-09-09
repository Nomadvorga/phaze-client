#version 330 core

uniform sampler2D MainSampler;
uniform sampler2D MainDepthSampler;

// 1.21.11 removed loose uniforms entirely - UniformType only offers
// UNIFORM_BUFFER / TEXEL_BUFFER, and a post-effect pass gets its values from a
// named std140 block declared in the post_effect JSON. The member ORDER here
// must match the order of the "MotionBlurConfig" entries in
// assets/phazeclient/post_effect/motion_blur.json, because PostEffectPass packs
// the buffer by walking that list through Std140Builder in sequence.
//
// std140 layout of this block (offsets in bytes):
//   mvInverse            0    mat4, 64
//   projInverse         64    mat4, 64
//   prevModelView      128    mat4, 64
//   prevProjection     192    mat4, 64
//   cameraPos          256    vec3 occupies 16 (12 + 4 pad)
//   prevCameraPos      272    vec3 occupies 16
//   view_res           288    vec2, 8
//   BlendFactor        296    float
//   inverseSamples     300    float
//   handDepthThreshold 304    float
//   motionBlurSamples  308    int
//   halfSamples        312    int
//   blurAlgorithm      316    int
//   total              320
layout(std140) uniform MotionBlurConfig {
    mat4 mvInverse;
    mat4 projInverse;
    mat4 prevModelView;
    mat4 prevProjection;
    vec3 cameraPos;
    vec3 prevCameraPos;
    vec2 view_res;
    float BlendFactor;
    float inverseSamples;
    float handDepthThreshold;
    int motionBlurSamples;
    int halfSamples;
    int blurAlgorithm;
};

in vec2 texCoord;
layout(location = 0) out vec4 color;

#define rcp(x) (1.0 / (x))

vec3 reproject(vec3 screen_pos) {
    vec3 ndc = screen_pos * 2.0 - 1.0;
    vec4 view_pos4 = projInverse * vec4(ndc, 1.0);
    if (abs(view_pos4.w) < 0.00001) {
        return vec3(screen_pos.xy, -1.0);
    }
    vec3 view_pos = view_pos4.xyz / view_pos4.w;

    vec3 world_pos = (mvInverse * vec4(view_pos, 1.0)).xyz + (cameraPos - prevCameraPos);
    vec4 prev_proj = prevProjection * (prevModelView * vec4(world_pos, 1.0));
    if (abs(prev_proj.w) < 0.00001) {
        return vec3(screen_pos.xy, -1.0);
    }

    return (prev_proj.xyz / prev_proj.w) * 0.5 + 0.5;
}

vec2 clampLength(vec2 velocity) {
    float lenSq = dot(velocity, velocity);
    return (lenSq > 0.16) ? velocity * (0.4 * inversesqrt(lenSq)) : velocity;
}

float noise(vec2 pos) {
    return fract(52.9829189 * fract(0.06711056 * pos.x + 0.00583715 * pos.y));
}

void main() {
    ivec2 texel = ivec2(gl_FragCoord.xy);
    vec4 source = texture(MainSampler, texCoord);

    float depth = texelFetch(MainDepthSampler, texel, 0).x;
    
    // Исключаем руки из размытия
    // Руки рендерятся с depth близким к 0 (очень близко к камере)
    // Используем настраиваемый порог для точной настройки
    if (depth < handDepthThreshold) {
        color = source;
        return;
    }
    
    vec3 previousPosition = reproject(vec3(texCoord, depth));
    // A post-effect can be scheduled before its history buffer has received a
    // first frame (resource reload, world join, or another renderer). Never
    // turn that transient state into a black frame: pass the source through
    // until the next valid history sample arrives.
    if (previousPosition.z < 0.0) {
        color = source;
        return;
    }

    vec2 velocity = texCoord - previousPosition.xy;
    velocity = clampLength(velocity);

    vec2 totalOffset = BlendFactor * velocity;

    // Ранний выход при пренебрежимо малом смещении.
    //
    // Выборки распределены по отрезку длиной totalOffset вокруг texCoord.
    // Если весь этот отрезок короче четверти пикселя, все выборки попадают
    // в один и тот же тексель, и цикл просто пересчитывает исходный пиксель:
    // sqrt(sum(c*c) / N) == c. Порог намеренно консервативный - разница
    // заведомо ниже кванта 1/255, поэтому качество не страдает, а
    // неподвижная камера перестаёт стоить целого прохода с выборками.
    vec2 safeResolution = max(view_res, vec2(1.0));
    vec2 spanPixels = totalOffset * safeResolution;
    if (dot(spanPixels, spanPixels) < 0.0625) {
        color = source;
        return;
    }

    int samples = clamp(motionBlurSamples, 1, 32);
    float safeInverseSamples = (inverseSamples > 0.0)
            ? inverseSamples
            : 1.0 / float(samples);
    vec2 baseStep = totalOffset * safeInverseSamples;

    vec3 color_sum = vec3(0.0);
    vec2 seed = texCoord * safeResolution;

    bool centerBlur = blurAlgorithm != 0;
    for (int i = 0; i < samples; ++i) {
        float fi = float(i);

        float jitter = noise(seed + vec2(fi, fi * 1.4));
        float offset_centered = fi - halfSamples;
        float sample_index = centerBlur ? offset_centered : fi;
        float sample_offset = sample_index + jitter;

        vec2 pos = texCoord + sample_offset * baseStep;
        vec3 color = texture(MainSampler, pos).rgb;

        color_sum += color * color;
    }
    color = vec4(sqrt(color_sum * safeInverseSamples), 1.0);
}
