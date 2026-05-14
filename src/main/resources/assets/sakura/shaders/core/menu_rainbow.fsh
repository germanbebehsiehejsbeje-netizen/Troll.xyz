#version 150

uniform float time;
uniform vec2 resolution;
uniform vec2 mouse;

out vec4 fragColor;

#define iTime time
            #define iResolution vec3(resolution, 0.0)
            #define iMouse vec4(mouse * resolution, 0.0, 0.0)

// --- 在这里粘贴 Shadertoy 代码 (mainImage 函数) ---

// 示例：简单的彩虹等离子效果
void mainImage( out vec4 fragColor, in vec2 fragCoord )
{
    vec2 uv = fragCoord/iResolution.xy;
    vec3 col = 0.5 + 0.5*cos(iTime+uv.xyx+vec3(0,2,4));
    fragColor = vec4(col,1.0);
}

// ------------------------------------------------

void main() {
    mainImage(fragColor, gl_FragCoord.xy);
    fragColor.a = 1.0;
}