#extension GL_OES_EGL_image_external : require
precision mediump float;
varying vec2 vTextureCoord;
uniform samplerExternalOES sTexture;
void main() {
    //    vec4 color = texture2D(sTexture, vTextureCoord);
    //    gl_FragColor = vec4(color.x +0.5, color.y +0.5, color.z+0.5, color.w);
    vec2 upper = vec2(vTextureCoord.x + 0.005f, vTextureCoord.y + 0.005f);
    vec2 downer = vec2(vTextureCoord.x - 0.005f, vTextureCoord.y - 0.005f);

    vec4 red = texture2D(sTexture, downer);
    vec4 green = texture2D(sTexture, upper);
    vec4 blue = texture2D(sTexture, vTextureCoord);
    gl_FragColor = vec4(red.x, green.y, blue.z, blue.w);
}