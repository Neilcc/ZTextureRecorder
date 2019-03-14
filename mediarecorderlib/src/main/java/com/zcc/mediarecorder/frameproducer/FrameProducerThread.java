package com.zcc.mediarecorder.frameproducer;

import android.opengl.EGLContext;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.Surface;

import com.zcc.mediarecorder.ALog;
import com.zcc.mediarecorder.frameproducer.gles.EglCore;
import com.zcc.mediarecorder.frameproducer.gles.FlatShadedProgram;
import com.zcc.mediarecorder.frameproducer.gles.FullFrameRect;
import com.zcc.mediarecorder.frameproducer.gles.GlUtil;
import com.zcc.mediarecorder.frameproducer.gles.Texture2dProgram;
import com.zcc.mediarecorder.frameproducer.gles.WindowSurface;

/**
 * This is a GL thread.
 * This thread cannot be reused since the GLContext is bind with surface.
 * Then GLContext and surface cannot be refreshed once it is created.
 * Thus, there is only start release and push frame in it.
 */
public class FrameProducerThread extends Thread {

    private static final String TAG = "FrameProducerThread";
    // Used to wait for the thread to doStart.
    private final Object mStartLock = new Object();
    private final Object WORK_MUTEX = new Object();
    private boolean mReady = false;
    private int w, h;
    private ProduceThreadHandler mDefaultHandler;
    private Surface mInputSurface;
    private WindowSurface mInputWindowSurface;
    // Used for off-screen rendering.
    private EglCore mEglCore;
    private int mOffscreenTexture;
    private int mFramebuffer;
    private int mDepthBuffer;
    private FullFrameRect mFullScreen;
    private FlatShadedProgram mProgram;
    private float[] mIdentityMatrix = new float[16];
    private EGLContext mEGLContext;
    private Texture2dProgram.ProgramType textureType;

    public FrameProducerThread(int w, int h, EGLContext context,
                               Texture2dProgram.ProgramType textureType,
                               Surface inputSurface) {
        super("encode" + System.nanoTime());
        this.w = w;
        this.h = h;
        this.mEGLContext = context;
        this.mInputSurface = inputSurface;
        this.textureType = textureType;
        if (mEGLContext == null) {
            throw new IllegalStateException("EGLContext must not be null");
        }
        Matrix.setIdentityM(mIdentityMatrix, 0);
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
            mStartLock.notify();
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
    private void waitUntilReady() {
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
        GlUtil.checkGlError("releaseGl doStart");
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
        GlUtil.checkGlError("draw doStart");
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
        private FrameProducerThread mFrameProducerThread;

        private ProduceThreadHandler(Looper looper) {
            super(looper);
            mFrameProducerThread = (FrameProducerThread) getLooper().getThread();
        }

        public void pushFrame(int textureId) {
            Message.obtain(this, NEW_FRAME, textureId, 0).sendToTarget();
        }

        public void queryStop() {
            Message.obtain(this, STOP).sendToTarget();
        }


        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case INIT:
                    break;
                case START:
                    break;
                case STOP:
                    Looper.myLooper().quit();
                    break;
                case NEW_FRAME:
                    mFrameProducerThread.pushFrame(msg.arg1);
                    break;
            }
        }

    }
}
