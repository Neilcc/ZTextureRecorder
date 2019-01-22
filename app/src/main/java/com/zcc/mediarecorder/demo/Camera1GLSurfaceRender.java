package com.zcc.mediarecorder.demo;

import android.app.Activity;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.os.Looper;

import com.zcc.mediarecorder.gles.FullFrameRect;
import com.zcc.mediarecorder.gles.Texture2dProgram;
import com.zcc.mediarecorder.demo.utils.Camera1Manager;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;


public class Camera1GLSurfaceRender implements GLSurfaceView.Renderer, Camera.PreviewCallback {

    private final Object MUTEX = new Object();
    private Camera1Manager mCamera1Manager;
    private int surfaceW = 0;
    private int surfaceH = 0;
    private int mCameraTextureId = 0;
    private FullFrameRect mScreenDisplay;
    private SurfaceTexture mCameraSurfaceTexture;
    private Handler mMainHandler;
    private Activity mActivity;
    private GLSurfaceView mGLSurface;
    private boolean tobeInit = true;
    private boolean isInited = false;
    private OnTextureRendListener onTextureRendListener;
    private BlockingQueue<Runnable> blockingQueue = new LinkedBlockingQueue<>();

    public Camera1GLSurfaceRender(Activity activity, GLSurfaceView mGLSurface) {
        this.mCamera1Manager = new Camera1Manager();
        this.mMainHandler = new Handler(Looper.getMainLooper());
        this.mActivity = activity;
        this.mGLSurface = mGLSurface;

    }

    public void setOnTextureRendListener(OnTextureRendListener onTextureRendListener) {
        this.onTextureRendListener = onTextureRendListener;
    }

    public void runOnGLThread(Runnable runnable) {
        blockingQueue.add(runnable);
    }

    public int getSurfaceW() {
        return surfaceW;
    }

    public int getSurfaceH() {
        return surfaceH;
    }

    public int getCameraW() {
        return mCamera1Manager.getPreviewSize().width;
    }

    public int getCameraH() {
        return mCamera1Manager.getPreviewSize().height;
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        isInited = true;
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        mScreenDisplay = new FullFrameRect(new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT));
        mCameraTextureId = mScreenDisplay.createTextureObject();
        mCameraSurfaceTexture = new SurfaceTexture(mCameraTextureId);
        GLES20.glClearColor(1.0f, 0.0f, 0.0f, 1.0f);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        synchronized (MUTEX) {
            this.surfaceH = height;
            this.surfaceW = width;
            if (tobeInit) {
                this.mMainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mCamera1Manager.initCamera(mActivity, surfaceW, surfaceH, mCameraSurfaceTexture,
                                Camera1GLSurfaceRender.this);
                        tobeInit = false;
                    }
                });
            }
        }
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glClearColor(1.0f, 0.0f, 0.0f, 1.0f);
        // todo
        GLES20.glViewport(0, 0, 1, 1);
        try {
            if (blockingQueue.size() > 0) {
                Runnable pendingRunnable = blockingQueue.poll(100, TimeUnit.MICROSECONDS);
                if (pendingRunnable != null) {
                    pendingRunnable.run();
                }
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (isInited) {
            float[] mtx = new float[16];
            mCameraSurfaceTexture.updateTexImage();
            mCameraSurfaceTexture.getTransformMatrix(mtx);
            mScreenDisplay.drawFrame(mCameraTextureId, mtx);
            if (onTextureRendListener != null) {
                onTextureRendListener.onFrame(mCameraTextureId);
            }
        }
        mGLSurface.requestRender();
    }

    public void initCamera() {
        synchronized (MUTEX) {
            if (surfaceW != 0 && surfaceH != 0)
                mCamera1Manager.initCamera(mActivity, surfaceW, surfaceH, mCameraSurfaceTexture,
                        Camera1GLSurfaceRender.this);
            else
                tobeInit = true;
        }
    }

    public interface OnTextureRendListener {
        void onFrame(int textureId);
    }
}
