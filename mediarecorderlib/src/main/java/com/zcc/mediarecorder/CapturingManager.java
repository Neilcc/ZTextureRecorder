package com.zcc.mediarecorder;

import android.opengl.EGL14;
import android.opengl.EGLContext;
import android.os.Build;
import android.os.Environment;

import com.zcc.mediarecorder.encoder.TextureMovieEncoder2;
import com.zcc.mediarecorder.frameproducer.FrameProducerThread;
import com.zcc.mediarecorder.frameproducer.gles.Texture2dProgram;

import java.io.File;

/**
 * It have not been decided whether this manager can be reprepared after stop,
 * hence let's do it in on shot temperately
 */
public class CapturingManager {

    private static final String TAG = "CapturingManager";
    private final Object MUTEX = new Object();
    private FrameProducerThread mFrameProducerThread;
    private String mVideoPath;
    private int mVideoWidth = 0;
    private int mVideoHeight = 0;
    private TextureMovieEncoder2 mTextureMovieEncoder;
    private volatile boolean isStarted = false;

    public CapturingManager() {
    }

    public static String getDirectoryDCIM() {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
                + File.separator;
    }

    public synchronized void initCapturing(int width, int height, String path,
                                           Texture2dProgram.ProgramType textureType,
                                           TextureMovieEncoder2.EncoderType encoderType,
                                           EGLContext eglContext) {
        if (this.mVideoWidth == 0) {
            this.mVideoWidth = width;
        }
        if (this.mVideoHeight == 0) {
            this.mVideoHeight = height;
        }
        this.mVideoPath = path;
        TextureMovieEncoder2.EncoderType mEncoderType = encoderType;
        EGLContext mEglContext = eglContext;
        if (eglContext == null || eglContext == EGL14.EGL_NO_CONTEXT) {
            mEglContext = EGL14.eglGetCurrentContext();
        }
        if (mEglContext == EGL14.EGL_NO_CONTEXT) {
            throw new IllegalStateException("texture egl context required");
        }
        if (mEncoderType == TextureMovieEncoder2.EncoderType.MEDIA_RECORDER
                && Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            throw new IllegalStateException("media recorder can only be used api >= 21");
        }
        if (mEncoderType == null) {
            mEncoderType = TextureMovieEncoder2.EncoderType.MEDIA_CODEC;
        }
        if (mTextureMovieEncoder != null) {
            mTextureMovieEncoder.doRelease();
            mTextureMovieEncoder = null;
        }
        if (mFrameProducerThread != null) {
            mFrameProducerThread.getHandler().queryStop();
            mFrameProducerThread = null;
        }
        mTextureMovieEncoder = new TextureMovieEncoder2(mVideoWidth, mVideoHeight, path,
                mEncoderType);
        mTextureMovieEncoder.doPrepare();

        // it cannot be reused, a new object when you want to reprepare
        mFrameProducerThread = new FrameProducerThread(mVideoWidth, mVideoHeight,
                mEglContext, textureType, mTextureMovieEncoder.getRecordSurface());
        mFrameProducerThread.start();
    }

    public synchronized boolean isStarted() {
        synchronized (MUTEX) {
            return isStarted;
        }
    }

    public void captureFrame(int textureId) {
        synchronized (MUTEX) {
            if (!isStarted) {
                return;
            }
        }
        mTextureMovieEncoder.onDrawFrame();
        mFrameProducerThread.getHandler().pushFrame(textureId);
    }

    public synchronized void startCapturing() {
        synchronized (MUTEX) {
            if (isStarted) {
                throw new IllegalStateException(" doStop capturing first");
            }
        }
        ALog.d(TAG, "--- startCapturing\tpath" + mVideoPath);
        mFrameProducerThread.startRecord();
        mTextureMovieEncoder.doStart();
        synchronized (MUTEX) {
            isStarted = true;
        }
    }

    public synchronized void stopCapturing() {
        synchronized (MUTEX) {
            if (!isStarted) return;
            isStarted = false;
        }
        ALog.d(TAG, "--- stopCapturing");
        mTextureMovieEncoder.doStop();
        mFrameProducerThread.getHandler().queryStop();
    }

    public synchronized void release() {
        mTextureMovieEncoder.doRelease();
    }

}
