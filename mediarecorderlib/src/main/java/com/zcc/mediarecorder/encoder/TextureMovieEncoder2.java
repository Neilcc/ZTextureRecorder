/*
 * Copyright 2014 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.zcc.mediarecorder.encoder;

import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.Surface;

import com.zcc.mediarecorder.ALog;
import com.zcc.mediarecorder.encoder.video.IVideoEncoderCore;
import com.zcc.mediarecorder.encoder.video.MediaCodecEncoderCore;

import java.io.IOException;
import java.lang.ref.WeakReference;

import static com.zcc.mediarecorder.encoder.TextureMovieEncoder2.Encoder.MEDIA_CODEC;

/**
 * Encode a movie from frames rendered from an external texture image.
 * <p>
 * The object wraps an encoder running partly on two different threads.  An external thread
 * is sending data to the encoder's input surface, and we (the encoder thread) are pulling
 * the encoded data out and feeding it into a MediaMuxer.
 * <p>
 * We could block forever waiting for the encoder, but because of the thread decomposition
 * that turns out to be a little awkward (we want to call signalEndOfInputStream() from the
 * encoder thread to avoid thread-safety issues, but we can't do that if we're blocked on
 * the encoder).  If we don't pull from the encoder often enough, the producer side can back up.
 * <p>
 * The solution is to have the producer trigger drainEncoder() on every frame, before it
 * submits the new frame.  drainEncoder() might run before or after the frame is submitted,
 * but it doesn't matter -- either it runs early and prevents blockage, or it runs late
 * and un-blocks the encoder.
 * <p>
 */
public class TextureMovieEncoder2 implements Runnable {
    private static final String TAG = "TextureMovieEncoder2";
    private static final boolean VERBOSE = false;
    private static final int MSG_STOP_RECORDING = 1;
    private static final int MSG_FRAME_AVAILABLE = 2;
    private final Object mReadyFence = new Object();      // guards ready/running
    // ----- accessed exclusively by encoder thread -----
    private IVideoEncoderCore mVideoEncoder;
    // ----- accessed by multiple threads -----
    private volatile EncoderHandler mHandler;
    private Handler mMainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean isPrepared = false;
    private boolean mReady;
    private boolean mRunning;

    /**
     * Tells the video recorder to start recording.  (Call from non-encoder thread.)
     * <p>
     * Creates a new thread, which will own the provided MediaCodecEncoderCore.  When the
     * thread exits, the MediaCodecEncoderCore will be released.
     * <p>
     * Returns after the recorder thread has started and is ready to accept Messages.
     */
    public TextureMovieEncoder2(int width, int height, String outputFile, Encoder encoder) {
        Log.d(TAG, "Encoder: startRecording()");
        if (encoder == null) {
            encoder = MEDIA_CODEC;
        }
        switch (encoder) {
            case MEDIA_CODEC:
                try {
                    mVideoEncoder = new MediaCodecEncoderCore(width, height, outputFile);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                isPrepared = true;
                break;
            case MEDIA_RECORDER:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    mVideoEncoder = new MediaRecorderEncoderCore(width, height, outputFile);
                    mMainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mVideoEncoder.prepare();
                            isPrepared = true;
                        }
                    });
                }
                break;
            default:
                throw new IllegalStateException("unexpected encoder type");
        }
        synchronized (mReadyFence) {
            if (mRunning) {
                Log.w(TAG, "Encoder thread already running");
                return;
            }
            mRunning = true;
            while (!isPrepared) {
            }
            new Thread(this, "TextureMovieEncoder").start();
            while (!mReady) {
                try {
                    mReadyFence.wait();
                } catch (InterruptedException ie) {
                    // ignore
                }
            }
        }
    }

    public Surface getRecordSurface() {
        return mVideoEncoder.getInputSurface();
    }

    public long getPTSUs() {
        return mVideoEncoder.getPTSUs();
    }

    public void startRecording() {
        mVideoEncoder.startRecording();
    }

    public void stopRecording() {
        mHandler.sendMessage(mHandler.obtainMessage(MSG_STOP_RECORDING));
    }

    public boolean isRecording() {
        synchronized (mReadyFence) {
            return mRunning;
        }
    }

    public void frameAvailableSoon() {
        synchronized (mReadyFence) {
            if (!mReady) {
                return;
            }
        }

        mHandler.sendMessage(mHandler.obtainMessage(MSG_FRAME_AVAILABLE));
    }

    @Override
    public void run() {
        // Establish a Looper for this thread, and define a Handler for it.
        Looper.prepare();
        synchronized (mReadyFence) {
            mHandler = new EncoderHandler(this);
            mVideoEncoder.prepare();
            mReady = true;
            mReadyFence.notify();
        }
        Looper.loop();

        Log.d(TAG, "Encoder thread exiting");
        synchronized (mReadyFence) {
            mReady = mRunning = false;
            mHandler = null;
        }
    }

    private void handleFrameAvailable() {
        ALog.dd("handleFrameAvailable");
        mVideoEncoder.drainEncoder(false);
    }

    /**
     * Handles a request to stop encoding.
     */
    private void handleStopRecording() {
        ALog.dd("handleStopRecording");
        mVideoEncoder.drainEncoder(true);
        mVideoEncoder.release();
    }

    public enum Encoder {
        MEDIA_RECORDER,
        // fixme  remain bugs in media codec
        @Deprecated
        MEDIA_CODEC
    }

    /**
     * Handles encoder state change requests.  The handler is created on the encoder thread.
     */
    private static class EncoderHandler extends Handler {
        private WeakReference<TextureMovieEncoder2> mWeakEncoder;

        public EncoderHandler(TextureMovieEncoder2 encoder) {
            mWeakEncoder = new WeakReference<>(encoder);
        }

        @Override  // runs on encoder thread
        public void handleMessage(Message inputMessage) {
            int what = inputMessage.what;
            Object obj = inputMessage.obj;

            TextureMovieEncoder2 encoder = mWeakEncoder.get();
            if (encoder == null) {
                Log.w(TAG, "EncoderHandler.handleMessage: encoder is null");
                return;
            }

            switch (what) {
                case MSG_STOP_RECORDING:
                    encoder.handleStopRecording();
                    Looper.myLooper().quit();
                    break;
                case MSG_FRAME_AVAILABLE:
                    encoder.handleFrameAvailable();
                    break;
                default:
                    throw new RuntimeException("Unhandled msg what=" + what);
            }
        }
    }

    public class EncoderException extends Exception {
        public EncoderException(String message) {
            super(message);
        }
    }
}
