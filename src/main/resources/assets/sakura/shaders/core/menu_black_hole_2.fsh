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

#define iTime time
#define iResolution vec3(resolution, 0.0)
#define iMouse vec4(mouse * resolution, 0.0, 0.0)

/*
 "Singularity" by @XorDev
 A whirling blackhole.
 Modified for deep purple theme.
*/

float hash(vec2 p) { return fract(sin(dot(p, vec2(123.4, 789.0)))* 43758.5453); }

vec3 getStars(vec2 uv) {
    vec3 col = vec3(0.0);
    float t = iTime * 0.15;

    for(float i=0.; i<3.; i++) {
        vec2 scale = uv * (12.0 + i * 10.0);
        vec2 id = floor(scale);
        vec2 gv = fract(scale) - 0.5;

        float n = hash(id + i * 55.1);

        if(n > 0.98) {
            float d = length(gv);
            float brightness = smoothstep(0.1, 0.0, d);
            brightness *= (0.5 + 0.5 * sin(t + n * 6.28));
            col += vec3(brightness);
        }
    }
    return col;
}

void mainImage(out vec4 O, vec2 F)
{
    float i = .2, a;
    vec2 r = iResolution.xy;
    vec2 p = ( F+F - r ) / r.y / .7;
    vec2 d = vec2(-1,1);
    vec2 b = p - i*d;
    
    // Manual matrix multiplication instead of mat2 constructor with vec4/complex args
    float dot_bb = dot(b,b);
    float factor = .1 + i/dot_bb;
    mat2 m1 = mat2(1.0, 1.0, -1.0/factor, 1.0/factor);
    vec2 c = p * m1;

    a = dot(c,c);
    float angle = .5*log(a) + iTime*i;
    float cos_a = cos(angle);
    float sin_33 = sin(angle + 33.0); // Simplified spiral rotation
    float sin_11 = sin(angle + 11.0);
    float cos_0 = cos(angle);
    
    mat2 m2 = mat2(cos_a, sin_33, sin_11, cos_0);
    vec2 v = (c * m2) / i;

    vec4 w = vec4(0.0);
    for(; i++<9.; ) {
        w += 1. + sin(v.xyyx);
        v += .7 * sin(v.yx * i + iTime) / i + .5;
    }

    i = length( sin(v/.3)*.4 + c*(3.+d) );

    vec4 mask = 1. - exp( -exp( c.x * vec4(0.6, -0.6, 0.0, 0.0) )
        / w.xyyx
        / ( 2. + i*i/4. - i )
        / ( .5 + 1. / a )
        / ( .03 + abs( length(p)-.7 ) )
    );

    vec3 darkBlue = vec3(0.05, 0.1, 0.5);
    vec3 lightBlue = vec3(0.2, 0.8, 1.0);

    vec3 nebula = mask.r * lightBlue + mask.g * darkBlue;
    vec3 stars = getStars(p);

    O = vec4(nebula + stars * (1.0 - length(nebula)), 1.0);
}

void main() {
    mainImage(fragColor, gl_FragCoord.xy);
}
