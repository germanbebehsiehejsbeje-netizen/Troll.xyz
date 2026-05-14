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

void main() {
    vec2 fragCoord = gl_FragCoord.xy;
    vec2 uv = (2.0 * fragCoord - iResolution.xy) / min(iResolution.x, iResolution.y);
    for (float i = 1.0; i < 10.0; i++) {
        uv.x += 0.6 / i * cos(i * 2.5 * uv.y + iTime);
        uv.y += 0.6 / i * cos(i * 1.5 * uv.x + iTime);
    }
    fragColor = vec4(vec3(0.1) / abs(sin(iTime - uv.y - uv.x)), 1.0);
}
