你是否常常因为产品需要录制屏幕而不要状态栏而烦闷 ？？

 你是不是常常因为产品需要录制直播视频而加班 ？？
 
你是不是常常因为要对一个surface同时录制两种Texture 分别存在的视频 （！！？？？）而恶心致死？

###### 嗯。。。我是第三种。于是便诞生了下面这个工具库。也希望能够解决对OpenGLES 和 视频编码不熟悉的同学的噩梦

##### ***ZTextureRecorder 的核心功能是，接收一个纹理，然后在每一帧绘制这个纹理（同时和外界的声音）最后编码成为一个视频文件。***

先上代码： [戳我看源码](https://github.com/Neilcc/ZTextureRecorder)


``目前ZTextureRecorder 支持 使用MediaRecorder 和MediaCodec(硬编码API) 进行录制。支持 Texture_2D普通纹理和 Texture_Ext Android扩展纹理作为输入。``

``需要注意的是，由于库本身不创建GL线程，因此需要传入GLContext 或者 在一个GL线程中初始化(以便通过Android EGL API 获取当前线程的 GLContext)。 这里会推荐使用GLSurfaceView 去承载和初始化GL上下文，当然如果是离屏渲染，可能需要自行创建GL线程及上下文。``


### 怎么使用？
#### 只需要五步就可以管理对texture 的录制，并轻松生成mp4 文件。

1. init capturing params: 用纹理的宽高， 文件地址、以及 纹理类型

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
## 结构和核心技术点

### 结构

主要由GL图片帧产生线程 [源码](https://github.com/Neilcc/ZTextureRecorder/blob/master/mediarecorderlib/src/main/java/com/zcc/mediarecorder/frameproducer/FrameProducerThread.java) 和编码核心模块组成。

这里主要是通过OpenGL 再次将Texture 渲染到编码核心模块提供的Surface 上，由于OpenGL 相关信息自成体系，并且较多，感兴趣的同学可以通过 [这篇文章](http://www.opengl-tutorial.org/cn/) 学习

其中编码核心模块分为mediaRecorder模块和mediaCodec 模块。

前者是高度集成的API，只包含简单调用。 [源码](https://github.com/Neilcc/ZTextureRecorder/blob/master/mediarecorderlib/src/main/java/com/zcc/mediarecorder/encoder/core/recorder/MediaRecorderEncoderCore.java)

后者中集成了音频录制核心 [源码](https://github.com/Neilcc/ZTextureRecorder/blob/master/mediarecorderlib/src/main/java/com/zcc/mediarecorder/encoder/core/codec/audio/AudioRecorderThread2.java) 和帧录制核心 [源码](https://github.com/Neilcc/ZTextureRecorder/blob/master/mediarecorderlib/src/main/java/com/zcc/mediarecorder/encoder/core/codec/MediaCodecEncoderCore.java) 。两个线程录制的同时需要通过Muxer 进行合成，以得到包含画面和声音的mp4 文件




