package com.zcc.mediarecorder;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaRecorder;

import java.io.IOException;
import java.nio.ByteBuffer;

public class AudioRecorderThread2 extends Thread {
    public static final String TAG = "AudioRecorderThread2";
    private static final String MIME_TYPE = "audio/mp4a-latm";
    private static final int SAMPLES_PER_FRAME = 1024;    // AAC, frameBytes/frame/channel
    private static final int FRAMES_PER_BUFFER = 25;    // AAC, frame/buffer/sec
    private static final int TIMEOUT_USEC = 10000;    // 10[microsec]
    private static final int SAMPLE_RATE = 44100;    // 44.1[KHz] is only setting guaranteed to be available on all devices.
    private static final int BIT_RATE = 64000;
    /*音轨数据源 mic就行吧?*/
    private static final int[] AUDIO_SOURCES = new int[]{
            MediaRecorder.AudioSource.MIC,
    };

    private final Object MUTEX = new Object();
    private volatile boolean isStart = false;
    private volatile boolean isExit = false;

    private MediaCodec mEncoder;                // API >= 16(Android4.1.2)
    private MediaFormat audioFormat;
    private MediaCodec.BufferInfo mBufferInfo;        // API >= 16(Android4.1.2)
    private android.media.AudioRecord audioRecord;

    private MuxerHolder mMuxerHolder;
    private int mTrackIndex;
    private int mBitRate;
    private long lastPresentTimeUs;


    public AudioRecorderThread2(MuxerHolder mMuxerHolder, int mBitRate) {
        mBufferInfo = new MediaCodec.BufferInfo();
        this.mBitRate = mBitRate;
        this.mMuxerHolder = mMuxerHolder;
        prepare();
    }

    private static final MediaCodecInfo selectAudioCodec(final String mimeType) {
        ALog.v(TAG, "selectAudioCodec:");

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
            for (int j = 0; j < types.length; j++) {
                ALog.d(TAG, "supportedType:" + codecInfo.getName() + ",MIME=" + types[j]);
                if (types[j].equalsIgnoreCase(mimeType)) {
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
        audioFormat = MediaFormat.createAudioFormat(MIME_TYPE, SAMPLE_RATE, 1);
        audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        audioFormat.setInteger(MediaFormat.KEY_CHANNEL_MASK, AudioFormat.CHANNEL_IN_MONO);
        audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, mBitRate);
        audioFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
//        audioFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, SAMPLE_RATE);
        ALog.i(TAG, "format: " + audioFormat);
    }


    private void startMediaCodec() throws IOException {
        if (mEncoder != null) {
            return;
        }
        mEncoder = MediaCodec.createEncoderByType(MIME_TYPE);
        mEncoder.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mEncoder.start();
        ALog.d(TAG, "prepare finishing");
        prepareAudioRecord();
        isStart = true;
    }

    private void stopMediaCodec() {
        if (audioRecord != null) {
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
            }
        }
        if (mEncoder != null) {
            mEncoder.stop();
            mEncoder.release();
            mEncoder = null;
        }
        isStart = false;
        ALog.e("angcyo-->", "stop audio 录制...");
    }

    public synchronized void restart() {
        isStart = false;
    }

    private void prepareAudioRecord() {
        if (audioRecord != null) {
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
        }

        try {
            final int min_buffer_size = android.media.AudioRecord.getMinBufferSize(
                    SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
            int buffer_size = SAMPLES_PER_FRAME * FRAMES_PER_BUFFER;
            if (buffer_size < min_buffer_size)
                buffer_size = ((min_buffer_size / SAMPLES_PER_FRAME) + 1) * SAMPLES_PER_FRAME * 2;

            audioRecord = null;
            for (final int source : AUDIO_SOURCES) {
                try {
                    audioRecord = new android.media.AudioRecord(source, SAMPLE_RATE,
                            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, buffer_size);
                    if (audioRecord.getState() != android.media.AudioRecord.STATE_INITIALIZED)
                        audioRecord = null;
                } catch (Exception e) {
                    audioRecord = null;
                }
                if (audioRecord != null) break;
            }
        } catch (final Exception e) {
            android.util.Log.e(TAG, "AudioThread#run", e);
        }

        if (audioRecord != null) {
            audioRecord.startRecording();
        }
    }

    public void queryExit() {
        synchronized (MUTEX) {
            isExit = true;
        }
    }

    @Override
    public void run() {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
        final ByteBuffer buf = ByteBuffer.allocateDirect(SAMPLES_PER_FRAME);
        int readBytes;
        while (true) {
            /*启动或者重启*/
            if (!isStart) {
                stopMediaCodec();
                try {
                    ALog.e("angcyo-->", "audio -- startMediaCodec...");
                    startMediaCodec();
                } catch (IOException e) {
                    e.printStackTrace();
                    isStart = false;
                }
            } else if (audioRecord != null) {
                buf.clear();
                readBytes = audioRecord.read(buf, SAMPLES_PER_FRAME);
                long presentTime = System.nanoTime();

                if (readBytes > 0) {
                    // set audio data to encoder
//                    presentTime = presentTime -
//                            (readBytes / SAMPLE_RATE ) / 1000000000;
//                    long presentTimeUs = presentTime/1000L;
//                    if(lastPresentTimeUs >= presentTimeUs){
//                        lastPresentTimeUs +=23219;
//                    }else {
//                        lastPresentTimeUs = presentTimeUs;
//                    }
                    buf.position(readBytes);
                    buf.flip();
                    ALog.e(TAG, "got none empty audio");
                    try {
                        boolean isEnd = encode(buf, readBytes, mMuxerHolder.getPTSUs());
                        if (isEnd) {
                            audioRecord.stop();
                            audioRecord.release();
                            mMuxerHolder.onReleaseAudioMux();
                            break;
                        }
                    } catch (Exception e) {
                        ALog.e("angcyo-->", "解码音频(Audio)数据 失败");
                        e.printStackTrace();
                        audioRecord.stop();
                        audioRecord.release();
                        mMuxerHolder.onReleaseAudioMux();
                        break;
                    }
                }
            }
            /**/
        }
    }

    private boolean encode(final ByteBuffer buffer, final int length, final long presentationTimeUs) {
        boolean isEnd = false;
        synchronized (MUTEX) {
            isEnd = isExit;
            if (isEnd) mEncoder.signalEndOfInputStream();
        }
        ALog.e(TAG, " encode start isend :" + isEnd);
        final ByteBuffer[] inputBuffers = mEncoder.getInputBuffers();
        final int inputBufferIndex = mEncoder.dequeueInputBuffer(TIMEOUT_USEC);
        /*向编码器输入数据*/
        if (inputBufferIndex >= 0) {
            ALog.d(TAG, "got avaliable encode buffer encode audio");
            final ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
            inputBuffer.clear();
            if (buffer != null) {
                inputBuffer.put(buffer);
            }
            ALog.d(TAG, "encode:queueInputBuffer");
            if (length <= 0) {
                // send EOS
//                    mIsEOS = true;
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
            // nothing to do here because MediaCodec#dequeueInputBuffer(TIMEOUT_USEC)
            // will wait for maximum TIMEOUT_USEC(10msec) on each call
            ALog.d(TAG, "encode try again latter");
            return false;
        }
        /*获取解码后的数据*/
//        if (mMuxerHolder.getMuxer() == null) {
//            if (DEBUG) ALog.w(TAG, "MediaMuxerRunnable is unexpectedly null");
//            return;
//        }
        ByteBuffer[] encoderOutputBuffers = mEncoder.getOutputBuffers();
        ALog.d(TAG, "Do media codec out put and mux");
        while (true) {
            int encoderStatus = mEncoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
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
//                    if (!endOfStream) {
//                        ALog.w(TAG, "reached end of stream unexpectedly");
//                    } else {
//                        if (VERBOSE) ALog.d(TAG, "end of stream reached");
//                    }
                    break;      // out of while
                }
            }
        }
        ALog.e(TAG, " encode start end :" + isEnd);
        return isEnd;
    }

}
