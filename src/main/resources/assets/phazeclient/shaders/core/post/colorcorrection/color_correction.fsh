#version 330 core

uniform sampler2D MainSampler;

// 1.21.11 removed loose uniforms - a post-effect pass gets its values from a
// named std140 block declared in the post_effect JSON. The member ORDER here
// must match the "ColorCorrectionConfig" entry order in
// assets/phazeclient/post_effect/color_correction.json, because PostEffectPass
// packs that buffer by walking the list through Std140Builder in sequence.
//
// std140: consecutive floats pack tightly at 4-byte alignment, so this block
// is 7 * 4 = 28 bytes.
layout(std140) uniform ColorCorrectionConfig {
    float Brightness;
    float Contrast;
    float Saturation;
    float Hue;
    float Gamma;
    float Temperature;
    float Vibrance;
};

in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

vec3 rgb2hsv(vec3 c) {
    vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
    vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
    vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
    float d = q.x - min(q.w, q.y);
    float e = 1.0e-10;
    return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
}

vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

// Каждая стадия обёрнута в проверку своего параметра.
//
// Ветвление идёт по uniform'у, то есть значение одинаково для всех
// фрагментов вокруг - дивергенции варпов нет, ветка стоит практически
// ничего. Раньше выполнялись все стадии подряд, включая две самых дорогих:
// полный круг rgb2hsv/hsv2rgb и pow(). Обе при своих дефолтах (Hue = 0,
// Gamma = 1) математически ничего не делают, но считались на каждый пиксель
// экрана каждый кадр, даже если пользователь трогал только, скажем,
// насыщенность.
void main() {
    vec3 color = texture(MainSampler, texCoord).rgb;

    // Brightness
    if (Brightness != 0.0) {
        color += Brightness;
    }

    // Contrast (around mid-gray)
    if (Contrast != 1.0) {
        color = (color - 0.5) * Contrast + 0.5;
    }

    // Saturation + Vibrance. lum берётся до применения насыщенности и
    // переиспользуется vibrance - ровно как в неветвлённой версии.
    if (Saturation != 1.0 || Vibrance != 0.0) {
        float lum = dot(color, vec3(0.299, 0.587, 0.114));

        if (Saturation != 1.0) {
            color = mix(vec3(lum), color, Saturation);
        }

        // Vibrance (boost low-saturation colors more)
        if (Vibrance != 0.0) {
            float maxC = max(color.r, max(color.g, color.b));
            float minC = min(color.r, min(color.g, color.b));
            float sat = maxC - minC;
            float vibranceScale = 1.0 + Vibrance * (1.0 - sat);
            color = mix(vec3(lum), color, vibranceScale);
        }
    }

    // Hue rotation. Самая дорогая стадия шейдера. При Hue = 0 круг
    // rgb2hsv -> hsv2rgb - тождество с точностью до ошибки float, но кламп
    // на входе в него влияет на результат, поэтому он сохранён отдельно.
    if (Hue != 0.0) {
        vec3 hsv = rgb2hsv(clamp(color, 0.0, 1.0));
        hsv.x = fract(hsv.x + Hue);
        color = hsv2rgb(hsv);
    } else {
        color = clamp(color, 0.0, 1.0);
    }

    // Temperature (shift blue <-> orange)
    if (Temperature != 0.0) {
        color.r += Temperature * 0.1;
        color.b -= Temperature * 0.1;
    }

    // Gamma. pow() - трансцендентная функция; при Gamma = 1 показатель
    // равен единице, и вызов тождественен. Финальный clamp ниже делает то
    // же, что делал max(color, 0.0) внутри pow.
    if (Gamma != 1.0) {
        color = pow(max(color, 0.0), vec3(1.0 / Gamma));
    }

    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
