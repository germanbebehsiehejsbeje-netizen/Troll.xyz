#version 150

layout(std140) uniform MenuUniforms {
    vec2 resolution;
    float time;
    float transition;
    vec2 mouse;
    vec2 uSize;
};

in vec2 vUv;
out vec4 fragColor;

#define iResolution vec3(resolution, 0.0)
#define iTime time

#define PI     3.1415926535897921284
#define REP    18
#define d2r(x) (x * PI / 180.0)
#define WBCOL  (vec3(0.5, 0.7,  1.7))
#define WBCOL2 (vec3(0.15, 0.8, 1.7))

float hash(vec2 p) {
    float h = dot(p, vec2(127.1, 311.7));
    return fract(sin(h) * 458.325421) * 2.0 - 1.0;
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);

    return mix(
        mix(hash(i + vec2(0.0, 0.0)), hash(i + vec2(1.0, 0.0)), f.x),
        mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x),
        f.y
    );
}

vec2 rot(vec2 p, float a) {
    float c = cos(a);
    float s = sin(a);
    return vec2(
        p.x * c - p.y * s,
        p.x * s + p.y * c
    );
}

float nac(vec3 p, vec2 F, vec3 o) {
    const float R = 0.0001;
    p += o;
    return length(max(abs(p.xy) - vec2(F), 0.0)) - R;
}

float recta(vec3 p, vec3 F, vec3 o) {
    const float R = 0.0001;
    p += o;
    return length(max(abs(p) - F, 0.0)) - R;
}

float map1(vec3 p, float scale) {
    float G = 0.50;
    float F = 0.50 * scale;
    float t = nac(p, vec2(F, F), vec3( G,  G, 0.0));
    t = min(t, nac(p, vec2(F, F), vec3( G, -G, 0.0)));
    t = min(t, nac(p, vec2(F, F), vec3(-G,  G, 0.0)));
    t = min(t, nac(p, vec2(F, F), vec3(-G, -G, 0.0)));
    return t;
}

float map2(vec3 p) {
    float t = map1(p, 0.9);
    t = max(t, recta(p, vec3(1.0, 1.0, 0.02), vec3(0.0, 0.0, 0.0)));
    return t;
}

float gennoise(vec2 p) {
    float d = 0.5;
    mat2 h = mat2(1.6, 1.2, -1.2, 1.6);

    float color = 0.0;
    for (int i = 0; i < 2; i++) {
        color += d * noise(p * 5.0 + iTime);
        p *= h;
        d *= 0.5;
    }
    return color;
}

void mainImage(out vec4 outColor, in vec2 fragCoord) {
    vec4 acc = vec4(0.0);

    for (int count = 0; count < 1; count++) {
        vec2 uv = -1.0 + 2.0 * (fragCoord.xy / iResolution.xy);
        uv *= 1.4;
        uv.x += hash(uv.xy + iTime + float(count)) / 512.0;
        uv.y += hash(uv.yx + iTime + float(count)) / 512.0;

        vec3 dir = normalize(vec3(uv * vec2(iResolution.x / iResolution.y, 1.0), 1.0 + sin(iTime) * 0.01));
        dir.xz = rot(dir.xz, d2r(70.0));
        dir.xy = rot(dir.xy, d2r(90.0));

        vec3 pos = vec3(-0.1 + sin(iTime * 0.3) * 0.1, 2.0 + cos(iTime * 0.4) * 0.1, -3.5);
        vec3 col = vec3(0.0);
        float t = 0.0;
        float M = 1.002;
        float bsh = 0.012;
        float dens = 0.0;

        for (int i = 0; i < REP * 16; i++) {
            float temp = map1(pos + dir * t, 0.6);
            if (temp < 0.2) {
                col += WBCOL * 0.005 * dens;
            }
            t += bsh * M;
            bsh *= M;
            dens += 0.025;
        }

        t = 0.0;
        float y = 0.0;
        for (int i = 0; i < REP; i++) {
            float temp = map2(pos + dir * t);
            if (temp < 0.025) {
                col += WBCOL2 * 0.5;
            }
            t += temp;
            y += 1.0;
        }

        col += ((2.0 + uv.x) * WBCOL2) + (y / (25.0 * 50.0));
        col += gennoise(dir.xz) * 0.5;
        col *= 1.0 - uv.y * 0.5;
        col *= vec3(0.05);
        col = pow(col, vec3(0.717));

        acc += vec4(col, 1.0);
    }

    outColor = acc;
}

void main() {
    mainImage(fragColor, gl_FragCoord.xy);
}
