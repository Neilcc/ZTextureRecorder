package com.zcc.mediarecorder;

import android.media.MediaMuxer;

import java.io.IOException;

public class MuxerHolder {

    private final Object mStartLock = new Object();
    private MediaMuxer mMuxer;
    private volatile boolean isAudioConfiged = false;
    private volatile boolean isFrameConfiged = false;
    private volatile boolean isStarted = false;
    private boolean isVideoFinished = false;
    private boolean isAudioFinished = false;
    private long firstTimeStampBase = 0;
    private long prevOutputPTSUs = 0;
    private long firstNanoTime = 0;

    public MuxerHolder(String outputFile) throws IOException {
        mMuxer = new MediaMuxer(outputFile,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
    }

    public void onFrame() {
        if (firstNanoTime == 0) {
            firstTimeStampBase = System.nanoTime();
            firstNanoTime = System.nanoTime();
        }
    }


    public synchronized void setAudioConfiged(boolean audioConfiged) {
        isAudioConfiged = audioConfiged;
        if (isAudioConfiged && isFrameConfiged) {
            ALog.d("muxholder", "muxer holder start");
            mMuxer.start();
            synchronized (mStartLock) {
                isStarted = true;
                mStartLock.notify();
            }
        }
    }

    public synchronized void setFrameConfiged(boolean frameConfiged) {
        isFrameConfiged = frameConfiged;
        if (isAudioConfiged && isFrameConfiged) {
            ALog.d("muxholder", "muxer holder start");
            mMuxer.start();
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

    private void release() {
        if (mMuxer == null) return;
        isAudioConfiged = false;
        isFrameConfiged = false;
        isStarted = false;
        mMuxer.stop();
        mMuxer.release();
        mMuxer = null;
    }

    /**
     * get next encoding presentationTimeUs
     *
     * @return
     */
    public synchronized long getPTSUs() {
        long result = 0, thisNanoTime = System.nanoTime();

        if (firstTimeStampBase == 0) {
            result = thisNanoTime;
        } else {
            if (firstNanoTime == 0) firstNanoTime = thisNanoTime;
            long elapsedTime = thisNanoTime - firstNanoTime;
            result = firstTimeStampBase + elapsedTime;
        }

        result = result / 1000L;

        if (result < prevOutputPTSUs) {
            result = (prevOutputPTSUs - result) + result;
        }

        if (result == prevOutputPTSUs) {
            // yep another magic number which I don't fucking know either.
            // AAC frame magic number;
            result += 43219;
        }

        return prevOutputPTSUs = result;
    }
}
