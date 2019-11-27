package com.zcc.lib.camera;

import android.app.Activity;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.os.Looper;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;


public class Camera1GLSurfaceRender implements GLSurfaceView.Renderer, Camera.PreviewCallback {

    private final Object MUTEX = new Object();

    private Camera1Manager mCamera1Manager;
    private int mSurfaceW = 0;
    private int mSurfaceH = 0;
    private int mCameraTextureId = 0;
    private IGLRender mGLRender;
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
        this.mGLRender = iglRender;
    }

    public void setOnTextureRendListener(OnTextureRendListener onTextureRendListener) {
        this.onTextureRendListener = onTextureRendListener;
    }

    public void runOnGLThread(Runnable runnable) {
        blockingQueue.add(runnable);
    }

    public int getSurfaceW() {
        return mSurfaceW;
    }

    public int getSurfaceH() {
        return mSurfaceH;
    }

    public int getCameraTextureW() {
        return mCamera1Manager.getPreviewSize().width;
    }

    public int getCameraTextureH() {
        return mCamera1Manager.getPreviewSize().height;
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        isInited = true;
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        mCameraTextureId = mGLRender.createTexture();
        mCameraSurfaceTexture = new SurfaceTexture(mCameraTextureId);
        GLES20.glClearColor(1.0f, 0.0f, 0.0f, 1.0f);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        synchronized (MUTEX) {
            this.mSurfaceH = height;
            this.mSurfaceW = width;
            if (tobeInit) {
                this.mMainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            mCamera1Manager.initCameraWithTargetSize(mActivity, mSurfaceW, mSurfaceH, mCameraSurfaceTexture,
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
            GLES20.glViewport(0, 0, mSurfaceW, mSurfaceH);
        } else {
            float ratioSurface = mSurfaceW * 1.0f / mSurfaceH;
            float ratioCam = mCamera1Manager.getPreviewSize().width * 1.0f / mCamera1Manager.getPreviewSize().height;
            if (ratioCam < ratioSurface) {
                int trimW = (int) (ratioCam * mSurfaceH);
                int delta = (mSurfaceW - trimW) / 2;
                GLES20.glViewport(delta, 0, trimW, mSurfaceH);
            } else {
                int trimH = (int) (mSurfaceW / ratioCam);
                int delta = (mSurfaceH - mCamera1Manager.getPreviewSize().height) / 2;
                GLES20.glViewport(0, delta, mSurfaceW, trimH);
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
            mGLRender.rend(mCameraTextureId, mtx);
            if (onTextureRendListener != null) {
                onTextureRendListener.onFrame(mCameraTextureId);
            }
        }
        mGLSurface.requestRender();
    }

    public void initCamera() {
        synchronized (MUTEX) {
            if (mSurfaceW != 0 && mSurfaceH != 0) {
                try {
                    mCamera1Manager.initCameraWithTargetSize(mActivity, mSurfaceW, mSurfaceH, mCameraSurfaceTexture,
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
