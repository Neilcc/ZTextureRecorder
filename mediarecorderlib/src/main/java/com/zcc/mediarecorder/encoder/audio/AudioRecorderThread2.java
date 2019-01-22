package com.zcc.mediarecorder.encoder.audio;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.util.Log;

import com.zcc.mediarecorder.ALog;
import com.zcc.mediarecorder.encoder.EncoderConfigs;
import com.zcc.mediarecorder.encoder.MuxerHolder;

import java.io.IOException;
import java.nio.ByteBuffer;

import static android.os.Process.setThreadPriority;

public class AudioRecorderThread2 extends Thread {
    public static final String TAG = "AudioRecorderThread2";
    private static final String MIME_TYPE = "audio/mp4a-latm";
    private static final int TIMEOUT_SEC = 10000;    // 10[microsec]

    private int mAudioBufSize = -1;

    private final Object MUTEX = new Object();
    private volatile boolean isStart = false;
    private volatile boolean isExit = false;

    private MediaCodec mEncoder;                // API >= 16(Android4.1.2)
    private MediaFormat audioFormat;
    private MediaCodec.BufferInfo mBufferInfo;        // API >= 16(Android4.1.2)
    private AudioRecord mAudioRecord;

    private MuxerHolder mMuxerHolder;
    private int mTrackIndex;

    public AudioRecorderThread2(MuxerHolder mMuxerHolder) {
        mBufferInfo = new MediaCodec.BufferInfo();
        this.mMuxerHolder = mMuxerHolder;
        prepare();
    }

    public static MediaCodecInfo selectAudioCodec(final String mimeType) {
        ALog.v(TAG, "selectAudioCodec:\t" + mimeType);
        MediaCodecInfo result = null;
        // get the list of available codecs
        final int numCodecs = MediaCodecList.getCodecCount();
        LOOP:
        for (int i = 0; i < numCodecs; i++) {
            final MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            if (!codecInfo.isEncoder()) {    // skipp decoder
                continue;
            }
            final String[] types = codecInfo.getSupportedTypes();
            for (String type : types) {
                ALog.d(TAG, "supportedType:" + codecInfo.getName() + ",MIME=" + type);
                if (type.equalsIgnoreCase(mimeType)) {
                    result = codecInfo;
                    break LOOP;
                }
            }
        }
        return result;
    }

    private void prepare() {
        MediaCodecInfo audioCodecInfo = selectAudioCodec(MIME_TYPE);
        if (audioCodecInfo == null) {
            ALog.e(TAG, "Unable to find an appropriate codec for " + MIME_TYPE);
            return;
        }
        ALog.i(TAG, "selected codec: " + audioCodecInfo.getName());
        audioFormat = MediaFormat.createAudioFormat(MIME_TYPE, EncoderConfigs.AUDIO_SAMPLE_RATE, 1);
        // AAC LC
        audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        // 单声道
        audioFormat.setInteger(MediaFormat.KEY_CHANNEL_MASK, AudioFormat.CHANNEL_IN_MONO);
        audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, EncoderConfigs.AUDIO_BIT_RATE);
        audioFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
        audioFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, EncoderConfigs.AUDIO_SAMPLE_RATE);
        ALog.i(TAG, "format: " + audioFormat);
    }


    private void startMediaCodec() throws IOException {
        if (mEncoder != null) {
            return;
        }
        mEncoder = MediaCodec.createEncoderByType(MIME_TYPE);
        mEncoder.configure(audioFormat, null, null,
                MediaCodec.CONFIGURE_FLAG_ENCODE);
        mEncoder.start();
        ALog.d(TAG, "prepare finishing");
        prepareAudioRecord();
        isStart = true;
    }

    private void stopMediaCodec() {
        if (mAudioRecord != null) {
            mAudioRecord.stop();
            mAudioRecord.release();
            mAudioRecord = null;
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                // unexpected
            }
        }
        if (mEncoder != null) {
            mEncoder.stop();
            mEncoder.release();
            mEncoder = null;
        }
        isStart = false;
        ALog.e(TAG, "stop audio 录制...");
    }

    public synchronized void restart() {
        isStart = false;
    }

    private void prepareAudioRecord() {
        if (mAudioRecord != null) {
            mAudioRecord.stop();
            mAudioRecord.release();
            mAudioRecord = null;
        }
        try {
            mAudioBufSize = android.media.AudioRecord.getMinBufferSize(
                    EncoderConfigs.AUDIO_SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
            //??????
            // int buffer_size = SAMPLES_PER_FRAME * FRAMES_PER_BUFFER;
            // if (buffer_size < min_buffer_size)
            // buffer_size = ((min_buffer_size / SAMPLES_PER_FRAME) + 1) * SAMPLES_PER_FRAME * 2;
            mAudioRecord = null;
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
            Log.e(TAG, "AudioThread#run", e);
        }
        if (mAudioRecord != null) {
            mAudioRecord.startRecording();
        }
    }

    public void queryExit() {
        synchronized (MUTEX) {
            isExit = true;
        }
    }

    @Override
    public void run() {
        setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
        ByteBuffer buf = null;
        int readBytes;
        while (true) {
            /*启动或者重启*/
            if (!isStart) {
                stopMediaCodec();
                try {
                    ALog.e("angcyo-->", "audio -- startMediaCodec...");
                    startMediaCodec();
                    buf = ByteBuffer.allocateDirect(mAudioBufSize);
                } catch (IOException e) {
                    e.printStackTrace();
                    isStart = false;
                }
            } else if (mAudioRecord != null) {
                if (buf == null) {
                    continue;
                }
                buf.clear();
                readBytes = mAudioRecord.read(buf, mAudioBufSize);
                if (readBytes > 0) {
                    buf.position(readBytes);
                    buf.flip();
                    ALog.e(TAG, "got none empty audio");
                    try {
                        boolean isEnd = encode(buf, readBytes, mMuxerHolder.getPTSUs());
                        if (isEnd) {
                            mAudioRecord.stop();
                            mAudioRecord.release();
                            mMuxerHolder.onReleaseAudioMux();
                            break;
                        }
                    } catch (Exception e) {
                        ALog.e("angcyo-->", "解码音频(Audio)数据 失败");
                        e.printStackTrace();
                        mAudioRecord.stop();
                        mAudioRecord.release();
                        mMuxerHolder.onReleaseAudioMux();
                        break;
                    }
                }
            }
        }
    }

    private boolean encode(final ByteBuffer buffer, final int length, final long presentationTimeUs) {
        boolean isEnd;
        synchronized (MUTEX) {
            isEnd = isExit;
            if (isEnd) mEncoder.signalEndOfInputStream();
        }
        ALog.e(TAG, " encode start isend :" + isEnd);
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
                mMuxerHolder.setAudioConfiged(true);
                ALog.d(TAG, "format configed ");
            } else if (encoderStatus < 0) {
                ALog.w(TAG, "unexpected result from encoder.dequeueOutputBuffer: " +
                        encoderStatus);
            } else {

                ALog.d(TAG, "start mux ");
                mMuxerHolder.waitUntilReady();
                final ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];
                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    // You shoud set output format to mMuxer here when you target Android4.3 or less
                    // but MediaCodec#getOutputFormat can not call here(because INFO_OUTPUT_FORMAT_CHANGED don't come yet)
                    // therefor we should expand and prepare output format from buffer data.
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
        ALog.e(TAG, " encode start end :" + isEnd);
        return isEnd;
    }

}
