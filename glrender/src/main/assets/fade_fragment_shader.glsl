#extension GL_OES_EGL_image_external : require
precision mediump float;
varying vec2 vTextureCoord;
uniform samplerExternalOES sTexture;
//uniform float uTextureCoordOffset;

void main() {
    gl_FragColor = texture2D(sTexture, vTextureCoord);
//    vec4 blue = texture2D(sTexture,vTextureCoord);
//    vec4 green = texture2D(sTexture,vec2(vTextureCoord.x + 1.0,vTextureCoord.y + 1.0));
//    vec4 red = texture2D(sTexture,vec2(vTextureCoord.x - 1.0 ,vTextureCoord.y - 1.0));
//    gl_FragColor = vec4(red.x,green.y,blue.z,blue.w);
}