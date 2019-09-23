package com.zcc.lib.camera;

import android.app.Activity;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.os.Looper;

import com.zcc.lib.IGLRender;

import java.io.IOException;
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
    //    private FullFrameRect mScreenDisplay;
    private IGLRender iglRender;
    private SurfaceTexture mCameraSurfaceTexture;
    private Handler mMainHandler;
    private Activity mActivity;
    private GLSurfaceView mGLSurface;
    private boolean tobeInit = true;
    private boolean isInited = false;
    private OnTextureRendListener onTextureRendListener;
    private BlockingQueue<Runnable> blockingQueue = new LinkedBlockingQueue<>();

    public Camera1GLSurfaceRender(Activity activity, GLSurfaceView mGLSurface, IGLRender iglRender) {
        this.mCamera1Manager = new Camera1Manager();
        this.mMainHandler = new Handler(Looper.getMainLooper());
        this.mActivity = activity;
        this.mGLSurface = mGLSurface;
        this.iglRender = iglRender;
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

        mCameraTextureId = iglRender.createTexture();
        mCameraSurfaceTexture = new SurfaceTexture(mCameraTextureId);
        GLES20.glClearColor(1.0f, 0.0f, 0.0f, 1.0f);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        synchronized (MUTEX) {
            this.surfaceH = height;
            this.surfaceW = width;
            if (tobeInit) {
                this.mMainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            mCamera1Manager.initCameraWithTargetSize(mActivity, surfaceW, surfaceH, mCameraSurfaceTexture,
                                    Camera1GLSurfaceRender.this);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        tobeInit = false;
                    }
                });
            }
        }
    }

    private void calcAndSetViewport() {
        if (mCamera1Manager.getPreviewSize() == null) {
            GLES20.glViewport(0, 0, surfaceW, surfaceH);
        } else {
            float ratioSurface = surfaceW * 1.0f / surfaceH;
            float ratioCam = mCamera1Manager.getPreviewSize().width * 1.0f / mCamera1Manager.getPreviewSize().height;
            if (ratioSurface >= ratioCam) {
                float trimHR = surfaceH * 1.0f / mCamera1Manager.getPreviewSize().height;
                int trimW = (int) (mCamera1Manager.getPreviewSize().height * trimHR);
                int delta = (surfaceW - trimW) / 2;
                GLES20.glViewport(delta, 0, trimW, surfaceH);
            } else {
                float trimWR = surfaceW * 1.0f / mCamera1Manager.getPreviewSize().height;
                int trimH = (int) (mCamera1Manager.getPreviewSize().height * trimWR);
                int delta = (surfaceH - mCamera1Manager.getPreviewSize().height) / 2;
                GLES20.glViewport(0, delta, surfaceW, trimH);
            }
        }
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        calcAndSetViewport();
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
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
//            mScreenDisplay.drawFrame(mCameraTextureId, mtx);
            iglRender.rend(mCameraTextureId, mtx);
            if (onTextureRendListener != null) {
                onTextureRendListener.onFrame(mCameraTextureId);
            }
        }
        mGLSurface.requestRender();
    }

    public void initCamera() {
        synchronized (MUTEX) {
            if (surfaceW != 0 && surfaceH != 0) {
                try {
                    mCamera1Manager.initCameraWithTargetSize(mActivity, surfaceW, surfaceH, mCameraSurfaceTexture,
                            Camera1GLSurfaceRender.this);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                tobeInit = true;
            }
        }
    }

    public interface OnTextureRendListener {
        void onFrame(int textureId);
    }
}
