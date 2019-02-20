package com.zcc.mediarecorder.encoder.core.codec.audio;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.zcc.mediarecorder.ALog;
import com.zcc.mediarecorder.common.ILifeCircle;
import com.zcc.mediarecorder.encoder.utils.EncoderConfigs;
import com.zcc.mediarecorder.encoder.core.codec.muxer.MuxerHolder;
import com.zcc.mediarecorder.encoder.utils.VideoUtils;

import java.io.IOException;
import java.nio.ByteBuffer;

import static android.os.Process.THREAD_PRIORITY_URGENT_AUDIO;

public class AudioRecorderThread2 implements ILifeCircle {
    public static final String TAG = "AudioRecorderThread2";
    private static final String MIME_TYPE = "audio/mp4a-latm";
    private static final int TIMEOUT_SEC = 10000;
    private final Object MUTEX = new Object();
    private int mAudioBufSize = -1;
    private volatile boolean isExit = false;

    private MediaFormat mAudioFormat;
    private MediaCodec.BufferInfo mBufferInfo;

    private MediaCodec mEncoder;
    private AudioRecord mAudioRecord;
    private MuxerHolder mMuxerHolder;

    private HandlerThread mWorkerHandlerThread;
    private AudioRecordHandler mAudioHandler;
    private int mTrackIndex;

    public AudioRecorderThread2(MuxerHolder muxerHolder) {
        mBufferInfo = new MediaCodec.BufferInfo();
        mMuxerHolder = muxerHolder;

    }

    private void innerReleaseAll() {
        mAudioFormat = null;
        mBufferInfo = null;
        mEncoder.release();
        mEncoder = null;
        mAudioRecord.release();
        mAudioRecord = null;
        mMuxerHolder.onReleaseAudioMux();
        mWorkerHandlerThread.quit();
        mWorkerHandlerThread = null;
        mAudioHandler = null;
    }

    @Override
    public void doStart() {
        mAudioHandler.doStart();
    }

    @Override
    public void doStop() {
        mAudioHandler.doStop();
    }

    @Override
    public void doRelease() {
        mAudioHandler.doRelease();
    }

    @Override
    public void doPrepare() {
        if (mWorkerHandlerThread == null) {
            mWorkerHandlerThread = new HandlerThread(TAG, THREAD_PRIORITY_URGENT_AUDIO);
            mWorkerHandlerThread.start();
            if (mAudioHandler == null) {
                mAudioHandler = new AudioRecordHandler(mWorkerHandlerThread.getLooper());
            }
            mAudioHandler.doPrepare();
        }
        mAudioHandler.doPrepare();
    }

    private void innerPrepare() {
        MediaCodecInfo audioCodecInfo = VideoUtils.selectAudioCodec(MIME_TYPE);
        if (audioCodecInfo == null) {
            ALog.e(TAG, "Unable to find an appropriate codec for " + MIME_TYPE);
            return;
        }
        ALog.i(TAG, "selected codec: " + audioCodecInfo.getName());
        if (mAudioFormat == null) {
            mAudioFormat = MediaFormat.createAudioFormat(MIME_TYPE, EncoderConfigs.AUDIO_SAMPLE_RATE, 1);
            // AAC LC
            mAudioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            // 单声道
            mAudioFormat.setInteger(MediaFormat.KEY_CHANNEL_MASK, AudioFormat.CHANNEL_IN_MONO);
            mAudioFormat.setInteger(MediaFormat.KEY_BIT_RATE, EncoderConfigs.AUDIO_BIT_RATE);
            mAudioFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
            mAudioFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, EncoderConfigs.AUDIO_SAMPLE_RATE);
            ALog.i(TAG, "format: " + mAudioFormat);
        }
        if (mAudioRecord == null) {
            try {
                mAudioBufSize = AudioRecord.getMinBufferSize(
                        EncoderConfigs.AUDIO_SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT);
                try {
                    mAudioRecord = new AudioRecord(EncoderConfigs.AUDIO_SOURCE,
                            EncoderConfigs.AUDIO_SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                            AudioFormat.ENCODING_PCM_16BIT, mAudioBufSize);
                    if (mAudioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                        mAudioRecord = null;
                    }
                } catch (Exception e) {
                    mAudioRecord = null;
                }
            } catch (final Exception e) {
                Log.e(TAG, "AudioThread#runRecord", e);
            }
        }
        if (mEncoder == null) {
            try {
                mEncoder = MediaCodec.createEncoderByType(MIME_TYPE);
                mEncoder.configure(mAudioFormat, null, null,
                        MediaCodec.CONFIGURE_FLAG_ENCODE);
            } catch (IOException e) {
                e.printStackTrace();
                Log.e(TAG, "AudioThread# encoder", e);
            }

        }
    }

    private void innerStart() {
        if (mEncoder != null) {
            mEncoder.start();
        }
        if (mAudioRecord != null) {
            mAudioRecord.startRecording();
        }
        ALog.d(TAG, "doPrepare finishing");
    }

    private void innerStop() {
        if (mAudioRecord != null) {
            mAudioRecord.stop();
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                // unexpected
            }
        }
        if (mEncoder != null) {
            mEncoder.stop();
        }
        if (mMuxerHolder != null) {
            mMuxerHolder.onReleaseAudioMux();
        }
        ALog.e(TAG, "doStop audio 录制...");
    }

    private void runRecord() {
        ByteBuffer buf = ByteBuffer.allocateDirect(mAudioBufSize);
        int readBytes = mAudioRecord.read(buf, mAudioBufSize);
        if (readBytes > 0) {
            buf.position(readBytes);
            buf.flip();
            ALog.e(TAG, "got none empty audio");
            try {
                boolean isEnd = encode(buf, readBytes, mMuxerHolder.getPTSUs());
                if (isEnd) {
                    mAudioHandler.doStop();
                }
            } catch (Exception e) {
                ALog.e("angcyo-->", "解码音频(Audio)数据 失败");
                e.printStackTrace();
                mAudioRecord.stop();
                mAudioRecord.release();
                mMuxerHolder.onReleaseAudioMux();
            }
        }
    }

    private boolean encode(final ByteBuffer buffer, final int length, final long presentationTimeUs) {
        boolean isEnd;
        synchronized (MUTEX) {
            isEnd = isExit;
            if (isEnd) mEncoder.signalEndOfInputStream();
        }
        ALog.e(TAG, " encode doStart isend :" + isEnd);
        final ByteBuffer[] inputBuffers = mEncoder.getInputBuffers();
        final int inputBufferIndex = mEncoder.dequeueInputBuffer(TIMEOUT_SEC);
        /*向编码器输入数据*/
        if (inputBufferIndex >= 0) {
            ALog.d(TAG, "got available encode buffer encode audio");
            final ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
            inputBuffer.clear();
            if (buffer != null) {
                inputBuffer.put(buffer);
            }
            ALog.d(TAG, "encode:queueInputBuffer");
            if (length <= 0) {
                ALog.e(TAG, "send BUFFER_FLAG_END_OF_STREAM");
                mEncoder.queueInputBuffer(inputBufferIndex, 0, 0,
                        presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            } else {
                ALog.d(TAG, "send BUFFER_FLAG_ **NOT** END_OF_STREAM");
                mEncoder.queueInputBuffer(inputBufferIndex, 0, length,
                        presentationTimeUs, 0);
            }
        } else if (inputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
            // wait for MediaCodec encoder is ready to encode
            // nothing to do here because MediaCodec#dequeueInputBuffer(TIMEOUT_SEC)
            // will wait for maximum TIMEOUT_SEC(10msec) on each call
            ALog.d(TAG, "encode try again latter");
            return false;
        }
        ByteBuffer[] encoderOutputBuffers = mEncoder.getOutputBuffers();
        ALog.d(TAG, "Do media codec out put and mux");
        while (true) {
            int encoderStatus = mEncoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT_SEC);
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                ALog.d(TAG, " drain try again latter");
                if (!isEnd) {
                    break;      // out of while
                } else {
                    ALog.d(TAG, "no output available, spinning to await EOS");
                }
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                encoderOutputBuffers = mEncoder.getOutputBuffers();
                ALog.d(TAG, "buffers changed");
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // this should only invoked at once
                MediaFormat newFormat = mEncoder.getOutputFormat();
                mTrackIndex = mMuxerHolder.getMuxer().addTrack(newFormat);
                mMuxerHolder.setAudioConfig(true);
                ALog.d(TAG, "format configed ");
            } else if (encoderStatus < 0) {
                ALog.w(TAG, "unexpected result from encoder.dequeueOutputBuffer: " +
                        encoderStatus);
            } else {

                ALog.d(TAG, "doStart mux ");
                mMuxerHolder.waitUntilReady();
                final ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];
                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    // You shoud set output format to mMuxer here when you target Android4.3 or less
                    // but MediaCodec#getOutputFormat can not call here(because INFO_OUTPUT_FORMAT_CHANGED don't come yet)
                    // therefor we should expand and doPrepare output format from buffer data.
                    // This sample is for API>=18(>=Android 4.3), just ignore this flag here
                    ALog.d(TAG, "drain:BUFFER_FLAG_CODEC_CONFIG" + mBufferInfo.size);
                    mBufferInfo.size = 0;
                }
                if (mBufferInfo.size != 0) {
//                    ALog.d("presentTime", "添加音频数据 " + mBufferInfo.presentationTimeUs);
//                    mBufferInfo.presentationTimeUs = presentationTimeUs;
                    mMuxerHolder.getMuxer().writeSampleData(mTrackIndex, encodedData, mBufferInfo);
                    ALog.d("presentTime", "sent " + mBufferInfo.size + " bytes to muxer, ts=" +
                            mBufferInfo.presentationTimeUs);
                }
                // return buffer to encoder
                mEncoder.releaseOutputBuffer(encoderStatus, false);
                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0 || mBufferInfo.size == 0) {
                    break;      // out of while
                }
            }
        }
        ALog.e(TAG, " encode doStart end :" + isEnd);
        return isEnd;
    }

    private class AudioRecordHandler extends Handler implements ILifeCircle {

        private static final int START = 0;
        private static final int STOP = 1;
        private static final int RELEASE = 2;
        private static final int PREPARE = 3;
        private static final int REPEAT = 4;
        private boolean isStarted = true;

        AudioRecordHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case START:
                    isStarted = true;
                    innerStart();
                    sendEmptyMessage(REPEAT);
                    break;
                case STOP:
                    isStarted = false;
                    innerStop();
                    break;
                case RELEASE:
                    innerReleaseAll();
                    break;
                case PREPARE:
                    innerPrepare();
                    break;
                case REPEAT:
                    if (!isStarted) {
                        return;
                    }
                    runRecord();
                    sendEmptyMessage(REPEAT);
                    break;
                default:
                    break;
            }
        }

        @Override
        public void doStart() {
            sendEmptyMessage(START);
        }

        @Override
        public void doStop() {
            sendEmptyMessage(STOP);
        }

        @Override
        public void doRelease() {
            sendEmptyMessage(RELEASE);
        }

        @Override
        public void doPrepare() {
            sendEmptyMessage(PREPARE);
        }
    }

}
