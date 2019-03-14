## GLMediaHub

### Introduction

 This is a library that supports record video from surface by MediaRecord or MediaCodec.

 openGLES part is based on [grafika](https://github.com/google/grafika)

### Libs and Apis

#### mediarecorderlib

This is a texture recorder library, which input is openGLES texture and output is mp4 files.

It supports for TEXTURE_2D and TEXTURE_EXT frames.

The encoder can be chosen between MediaRecorder(which requires Android L or Above) and MediaCodec.

##### This lib can be used to record screen, rtmp surface, offscreen textures, and any texture that obtained from a OpenGL thread.

##### Remind that it do not create OpenGLES thread, it is necessary to pass a OpenGLES Context into it Or init it in a GL thread, such that I can get GLContext from current thread. Thus it is necessary to create OpenGLES context in your own project. A GLSurfaceView is recommended.

##### It can easily used by four steps:


1. init capturing params:

```java

    capturingManager.initCapturing(textureWidth, textureHeight,
                        toSaveFilePath,
                        Texture2dProgram.ProgramType.TEXTURE_EXT,
                        encoderType, eglContext);
```

2. start capturing manager:

```java

   capturingManager.startCapturing()

```

3. pass texture of each frame into it

```java

   capturingManager.captureFrame(textureId)

```

4. stop at proper time

```java

    capturingManger.stop();

```

5. release on component destroyed

```java

    capturingManger.release();

```

For more information, just run the demo or contact me by : zhuchengcheng@zju.edu.cn

#### mediadecoderlib

A empty lib currently.

#### glrender

A empty lib currently.

