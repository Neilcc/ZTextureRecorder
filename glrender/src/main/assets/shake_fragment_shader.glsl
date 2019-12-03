#extension GL_OES_EGL_image_external : require
precision mediump float;
varying vec2 vTextureCoord;
uniform samplerExternalOES sTexture;

//随机函数
float nrand(in float x, in float y){
    return fract(sin(dot(vec2(x, y), vec2(12.9898, 78.233))) * 43758.5453);
}


void main() {
    float u = vTextureCoord.x;
    float v = vTextureCoord.y;
    float jitter = nrand(u, v) * 2.0 - 1.0;

    jitter = jitter * 0.005f;

    vec4 color1 = texture2D(sTexture, fract(vec2(u + jitter, v + jitter)));

    gl_FragColor = vec4(color1.r, color1.g, color1.b, color1.a);
    //    vec4 color = texture2D(sTexture, vTextureCoord);
    //    gl_FragColor = vec4(color.x +0.5, color.y +0.5, color.z+0.5, color.w);
}