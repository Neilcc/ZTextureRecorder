package com.zcc.mediarecorder;

import android.opengl.EGLContext;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.Surface;

import com.zcc.mediarecorder.gles.EglCore;
import com.zcc.mediarecorder.gles.FlatShadedProgram;
import com.zcc.mediarecorder.gles.FullFrameRect;
import com.zcc.mediarecorder.gles.GlUtil;
import com.zcc.mediarecorder.gles.Texture2dProgram;
import com.zcc.mediarecorder.gles.WindowSurface;

/**
 * This is a GL thread.
 */
class ProduceFrameGLThread extends Thread {

    private static final String TAG = "ProduceFrameGLThread";
    // Used to wait for the thread to start.
    private final Object mStartLock = new Object();
    private final Object WORK_MUTEX = new Object();
    private WindowSurface mInputWindowSurface;
    private boolean mReady = false;
    private EglCore mEglCore;
    private ProduceThreadHandler mDefaultHandler;
    // Used for off-screen rendering.
    private int mOffscreenTexture;
    private int mFramebuffer;
    private int mDepthBuffer;
    private FullFrameRect mFullScreen;
    private FlatShadedProgram mProgram;
    private float[] mIdentityMatrix = new float[16];
    private EGLContext mEGLContext;
    private Texture2dProgram.ProgramType textureType;
    private int w, h;
    private String output;
    private Surface mInputSurface;

    ProduceFrameGLThread(int w, int h, String output, EGLContext context,
                         Texture2dProgram.ProgramType textureType) {
        super("encode" + System.nanoTime());
        this.w = w;
        this.h = h;
        this.output = output;
        this.mEGLContext = context;
        if (mEGLContext == null) {
            throw new IllegalStateException("EGLContext must not be null");
        }
        Matrix.setIdentityM(mIdentityMatrix, 0);
        this.textureType = textureType;
    }

    private void stopGLSurface() {
        if (mInputWindowSurface != null) {
            mInputWindowSurface.release();
            mInputWindowSurface = null;
        }
    }

    public void bindInputSurface(Surface inputSurface) {
        mInputSurface = inputSurface;
    }

    public ProduceThreadHandler getHandler() {
        return mDefaultHandler;
    }


    @Override
    public void run() {
        Looper.prepare();
        mDefaultHandler = new ProduceThreadHandler(Looper.myLooper());
        if (mEGLContext != null) {
            mEglCore = new EglCore(mEGLContext, EglCore.FLAG_RECORDABLE | EglCore.FLAG_TRY_GLES3);
        } else {
            throw new RuntimeException("empty GLContext");
        }
        prepareGl(mInputSurface);
        synchronized (mStartLock) {
            mReady = true;
            mStartLock.notify();    // signal waitUntilReady()
        }
        Looper.loop();
        ALog.d(TAG, "looper quit");
        releaseGl();
        mEglCore.release();
        synchronized (mStartLock) {
            mReady = false;
        }
    }

    /**
     * Waits until the  thread is ready to receive messages.
     * <p>
     * Call from the UI thread.
     */
    public void waitUntilReady() {
        synchronized (mStartLock) {
            while (!mReady) {
                try {
                    mStartLock.wait();
                } catch (InterruptedException ie) { /* not expected */ }
            }
        }
    }

    public void startRecord() {
        waitUntilReady();
    }

    /**
     * Releases most of the GL resources we currently hold.
     * <p>
     * Does not toRelease EglCore.
     */
    private void releaseGl() {
        GlUtil.checkGlError("releaseGl start");
        int[] values = new int[1];
        if (mInputWindowSurface != null) {
            mInputWindowSurface.release();
            mInputWindowSurface = null;
        }
        if (mProgram != null) {
            mProgram.release();
            mProgram = null;
        }
        if (mOffscreenTexture > 0) {
            values[0] = mOffscreenTexture;
            GLES20.glDeleteTextures(1, values, 0);
            mOffscreenTexture = -1;
        }
        if (mFramebuffer > 0) {
            values[0] = mFramebuffer;
            GLES20.glDeleteFramebuffers(1, values, 0);
            mFramebuffer = -1;
        }
        if (mDepthBuffer > 0) {
            values[0] = mDepthBuffer;
            GLES20.glDeleteRenderbuffers(1, values, 0);
            mDepthBuffer = -1;
        }
        if (mFullScreen != null) {
            mFullScreen.release(false);
            mFullScreen = null;
        }
        GlUtil.checkGlError("releaseGl done");
        mEglCore.makeNothingCurrent();
    }

    private void queryStop() {
        synchronized (WORK_MUTEX) {
            stopGLSurface();
        }
    }

    private void pushFrame(int textureID) {
        synchronized (WORK_MUTEX) {
            ALog.d(TAG, "onframe texture " + textureID);
            draw(textureID);
        }
    }

    private void draw(int textureID) {
        if (mInputWindowSurface == null) {
            return;
        }
        mInputWindowSurface.makeCurrent();
        GlUtil.checkGlError("draw start");
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glViewport(0, 0, w, h);
        mFullScreen.drawFrame(textureID, mIdentityMatrix);
        GlUtil.checkGlError("draw done");
        mInputWindowSurface.swapBuffers();
    }

    private void prepareGl(Surface surface) {
        ALog.d(TAG, "prepareGl");
        mInputWindowSurface = new WindowSurface(mEglCore, surface, false);
        mInputWindowSurface.makeCurrent();
        mFullScreen = new FullFrameRect(new Texture2dProgram(textureType));
        mProgram = new FlatShadedProgram();
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
    }

    public static class ProduceThreadHandler extends Handler {

        private static final int INIT = 0;
        private static final int START = 1;
        private static final int STOP = 2;
        private static final int NEW_FRAME = 3;
        private final Object MUTEX = new Object();
        private ProduceFrameGLThread mProduceFrameGLThread;
        private boolean isStarted = false;

        ProduceThreadHandler(Looper looper) {
            super(looper);
            mProduceFrameGLThread = (ProduceFrameGLThread) getLooper().getThread();
        }

        void pushFrame(int textureId) {
            Message.obtain(this, NEW_FRAME, textureId, 0).sendToTarget();
        }

        void queryStop() {
            Message.obtain(this, STOP).sendToTarget();
        }

        public void setStarted(boolean started) {
            synchronized (MUTEX) {
                isStarted = started;
            }
        }

        @Override
        public void handleMessage(Message msg) {
            synchronized (MUTEX) {
                if (!isStarted) {
                    return;
                }
            }
            switch (msg.what) {
                case INIT:
                    break;
                case START:
                    break;
                case STOP:
                    mProduceFrameGLThread.stopGLSurface();
                    Looper.myLooper().quit();
                    break;
                case NEW_FRAME:
                    mProduceFrameGLThread.pushFrame(msg.arg1);
                    break;
            }
        }
    }
}
