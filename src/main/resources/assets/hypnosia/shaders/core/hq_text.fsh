#version 330

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in vec2 texCoord;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    float distance = texture(Sampler0, texCoord).a;
    float smoothing = max(fwidth(distance) * 0.75, 0.012);
    float alpha = smoothstep(0.5 - smoothing, 0.5 + smoothing, distance);
    vec4 color = vec4(vertexColor.rgb, vertexColor.a * alpha);
    if (color.a <= 0.001) {
        discard;
    }
    fragColor = color;
}
