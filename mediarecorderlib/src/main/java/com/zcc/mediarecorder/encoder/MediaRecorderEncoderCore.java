package com.zcc.mediarecorder.encoder;

import android.media.MediaRecorder;
import android.os.Build;
import android.view.Surface;

import com.zcc.mediarecorder.EventManager;
import com.zcc.mediarecorder.common.ErrorCode;
import com.zcc.mediarecorder.encoder.video.IVideoEncoderCore;

import java.io.IOException;

import androidx.annotation.RequiresApi;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class MediaRecorderEncoderCore implements IVideoEncoderCore {
    private MediaRecorder mMediaRecorder;
    private int w, h;
    private String output;

    MediaRecorderEncoderCore(int w, int h, String output) {
        this.w = w;
        this.h = h;
        this.output = output;
    }

    public void prepare() {
        mMediaRecorder = new MediaRecorder();
        mMediaRecorder.reset();
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mMediaRecorder.setVideoEncodingBitRate(VideoUtils.getBitRate(w, h));
        mMediaRecorder.setVideoSize(w, h);
        mMediaRecorder.setVideoFrameRate(30);
        mMediaRecorder.setOutputFile(output);
        mMediaRecorder.setOnErrorListener(new MediaRecorder.OnErrorListener() {
            @Override
            public void onError(MediaRecorder mr, int what, int extra) {
                EventManager.get().sendMsg(ErrorCode.ERROR_MEDIA_COMMON,
                        "mediarecorder erorr: what:" + what + "extra: " + extra + "msg");
                mr.release();
            }
        });
        mMediaRecorder.setOnInfoListener(new MediaRecorder.OnInfoListener() {
            @Override
            public void onInfo(MediaRecorder mr, int what, int extra) {
                android.util.Log.i("zcc", "mr info" + what + "extra" + extra);
            }
        });
        //setup audio
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mMediaRecorder.setAudioEncodingBitRate(44800);
        try {
            mMediaRecorder.prepare();
        } catch (IOException e) {
            e.printStackTrace();
            EventManager.get().sendMsg(ErrorCode.ERROR_MEDIA_COMMON,
                    "media Recorder prepare error: " + e.getMessage());
        }
    }

    @Override
    public Surface getInputSurface() {
        return mMediaRecorder.getSurface();
    }

    @Override
    public void release() {
        mMediaRecorder.stop();
        mMediaRecorder.reset();
    }

    @Override
    public void drainEncoder(boolean endOfStream) {
// ignore
    }

    @Override
    public void startRecording() {
        mMediaRecorder.start();
    }

    @Override
    public long getPTSUs() {
        return System.nanoTime() / 1000L;
    }
}
