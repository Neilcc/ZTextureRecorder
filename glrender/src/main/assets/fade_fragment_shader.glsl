#extension GL_OES_EGL_image_external : require
precision mediump float;
varying vec2 vTextureCoord;
uniform samplerExternalOES sTexture;

void main() {
    vec4 color = texture2D(sTexture, vTextureCoord);
    gl_FragColor = vec4(color.x +0.5, color.y +0.5, color.z+0.5, color.w);
}
