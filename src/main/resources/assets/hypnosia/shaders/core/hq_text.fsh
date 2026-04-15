#version 330

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in vec2 texCoord;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec4 sampled = texture(Sampler0, texCoord);
    vec4 color = sampled * vertexColor * ColorModulator;
    if (color.a <= 0.001) {
        discard;
    }
    fragColor = color;
}
