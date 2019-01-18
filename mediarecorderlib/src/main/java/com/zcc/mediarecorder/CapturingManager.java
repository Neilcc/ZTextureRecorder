package com.zcc.mediarecorder;

import android.opengl.EGL14;
import android.opengl.EGLContext;
import android.os.Environment;

import com.zcc.mediarecorder.encoder.TextureMovieEncoder2;
import com.zcc.mediarecorder.gles.Texture2dProgram;

import java.io.File;

public class CapturingManager {

    private static final String TAG = "CapturingManager";
    private ProduceFrameThread mProduceFrameThread;

    private String mVideoPath;
    private int mVideoWidth = 0;
    private int mVideoHeight = 0;
    private boolean isStarted = false;
//    private IVideoEncoderCore mVideoEncoderCore;

    public CapturingManager() {
    }

    public static String getDirectoryDCIM() {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
                + File.separator;
    }

    public synchronized void initCapturing(int width, int height, String path,
                                           Texture2dProgram.ProgramType textureType,
                                           TextureMovieEncoder2.Encoder encoderType,
                                           EGLContext eglContext) {
        if (this.mVideoWidth == 0) this.mVideoWidth = width;
        if (this.mVideoHeight == 0) this.mVideoHeight = height;
        this.mVideoPath = path;
        if (eglContext == null || eglContext == EGL14.EGL_NO_CONTEXT) {
            eglContext = EGL14.eglGetCurrentContext();
        }
        if (eglContext == EGL14.EGL_NO_CONTEXT) {
            throw new IllegalStateException("texture egl context required");
        }
        mProduceFrameThread = new ProduceFrameThread(width, height, path, eglContext, textureType, encoderType);
        mProduceFrameThread.start();
    }

    public void checkSize(int w, int h) {
        if ((w & 1) != 0 || (h & 1) != 0) throw new IllegalStateException("invalid size");
    }


    public void startCapturing() {
        ALog.d(TAG, "--- startCapturing\tpath" + mVideoPath);
        mProduceFrameThread.startRecord();
        isStarted = true;
    }

    public boolean isStarted() {
        return isStarted;
    }

    public void captureFrame(int textureId) {
        mProduceFrameThread.getHandler().pushFrame(textureId);
    }

    public void stopCapturing() {
        ALog.d(TAG, "--- stopCapturing");
        mProduceFrameThread.getHandler().queryStop();
        isStarted = false;

    }

}
