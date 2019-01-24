package com.zcc.mediarecorder.encoder;

import android.media.MediaMuxer;

import com.zcc.mediarecorder.ALog;
import com.zcc.mediarecorder.common.ILifeCircle;

import java.io.IOException;

public class MuxerHolder implements ILifeCircle {

    private final Object mStartLock = new Object();
    private MediaMuxer mMuxer;
    private volatile boolean isAudioConfig = false;
    private volatile boolean isFrameConfig = false;
    private volatile boolean isStarted = false;
    private boolean isVideoFinished = false;
    private boolean isAudioFinished = false;
    private long firstNanoTime = 0;

    public MuxerHolder(String outputFile) throws IOException {
        mMuxer = new MediaMuxer(outputFile,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
    }

    public void onFrame() {
        if (firstNanoTime == 0) {
            firstNanoTime = System.nanoTime();
        }
    }


    public synchronized void setAudioConfig(boolean audioConfig) {
        isAudioConfig = audioConfig;
        if (isAudioConfig && isFrameConfig) {
            ALog.d("muxholder", "muxer holder start");
            start();
            synchronized (mStartLock) {
                isStarted = true;
                mStartLock.notify();
            }
        }
    }

    public synchronized void setFrameConfig(boolean frameConfig) {
        isFrameConfig = frameConfig;
        if (isAudioConfig && isFrameConfig) {
            ALog.d("muxholder", "muxer holder start");
            start();
            synchronized (mStartLock) {
                isStarted = true;
                mStartLock.notify();
            }
        }
    }

    public MediaMuxer getMuxer() {
        return mMuxer;
    }

    public void waitUntilReady() {
        synchronized (mStartLock) {
            while (!isStarted) {
                try {
                    mStartLock.wait();
                } catch (InterruptedException ie) { /* not expected */ }
            }
        }
    }

    public synchronized void onReleaseFrameMux() {
        isVideoFinished = true;
        if (isAudioFinished) {
            release();
        }
    }

    public synchronized void onReleaseAudioMux() {
        isAudioFinished = true;
        if (isVideoFinished) {
            release();
        }
    }

    @Override
    public void start() {
        mMuxer.start();
    }

    @Override
    public void stop() {
        if (mMuxer == null) {
            return;
        }
        isAudioConfig = false;
        isFrameConfig = false;
        isStarted = false;
        mMuxer.stop();
    }

    @Override
    public void release() {
        if (mMuxer == null) return;
        mMuxer.release();
        mMuxer = null;
    }

    @Override
    public void prepare() {

    }

    public synchronized long getPTSUs() {
        return System.nanoTime() / 1000L;
    }
}
