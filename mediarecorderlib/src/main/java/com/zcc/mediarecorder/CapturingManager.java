package com.zcc.mediarecorder;

import android.opengl.EGL14;
import android.opengl.EGLContext;
import android.os.Build;
import android.os.Environment;

import com.zcc.mediarecorder.encoder.TextureMovieEncoder2;
import com.zcc.mediarecorder.gles.Texture2dProgram;

import java.io.File;

public class CapturingManager {

    private static final String TAG = "CapturingManager";
    private ProduceFrameGLThread mProduceFrameGLThread;
    private String mVideoPath;
    private int mVideoWidth = 0;
    private int mVideoHeight = 0;
    private String mFilePath = "";
    private EGLContext mEglContext;
    private Texture2dProgram.ProgramType mTextureType;
    private TextureMovieEncoder2.EncoderType mEncoderType;
    private TextureMovieEncoder2 mTextureMovieEncoder;
    private boolean isStarted = false;

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
        this.mTextureType = textureType;
        this.mEncoderType = encoderType;
        this.mEglContext = eglContext;
        if (eglContext == null || eglContext == EGL14.EGL_NO_CONTEXT) {
            eglContext = EGL14.eglGetCurrentContext();
        }
        if (eglContext == EGL14.EGL_NO_CONTEXT) {
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
            mTextureMovieEncoder.release();
            mTextureMovieEncoder = null;
        }
        if (mProduceFrameGLThread != null) {
            mProduceFrameGLThread.getHandler().queryStop();
            mProduceFrameGLThread = null;
        }
        mProduceFrameGLThread = new ProduceFrameGLThread(mVideoWidth, mVideoHeight,
                mFilePath, mEglContext, mTextureType);
        mProduceFrameGLThread.start();

        mTextureMovieEncoder = new TextureMovieEncoder2(mVideoWidth, mVideoHeight, mFilePath,
                mEncoderType);
        mTextureMovieEncoder.prepare();
        mProduceFrameGLThread.bindInputSurface(mTextureMovieEncoder.getRecordSurface());
    }

    public synchronized boolean isStarted() {
        return isStarted;
    }

    public void captureFrame(int textureId) {
        mTextureMovieEncoder.frameAvailableSoon();
        mProduceFrameGLThread.getHandler().pushFrame(textureId);
    }

    public synchronized void startCapturing() {
        if (isStarted) {
            throw new IllegalStateException(" stop capturing first");
        }
        ALog.d(TAG, "--- startCapturing\tpath" + mVideoPath);
        mProduceFrameGLThread.startRecord();
        mTextureMovieEncoder.start();
        isStarted = true;
    }

    public synchronized void stopCapturing() {
        if (!isStarted) return;
        isStarted = false;
        ALog.d(TAG, "--- stopCapturing");
        mProduceFrameGLThread.getHandler().queryStop();
    }

    public synchronized void release() {
        // should reInit before start
    }

}
