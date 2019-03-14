package com.zcc.mediarecorder.encoder.core.recorder;

import android.media.MediaRecorder;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.view.Surface;

import com.zcc.mediarecorder.ALog;
import com.zcc.mediarecorder.EventManager;
import com.zcc.mediarecorder.common.ErrorCode;
import com.zcc.mediarecorder.encoder.core.IMovieEncoderCore;
import com.zcc.mediarecorder.encoder.utils.VideoUtils;

import java.io.IOException;


@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class MediaRecorderEncoderCore implements IMovieEncoderCore {
    private MediaRecorder mMediaRecorder;
    private int w, h;
    private String outputFile;

    public MediaRecorderEncoderCore(int w, int h, String output) {
        this.w = w;
        this.h = h;
        this.outputFile = output;
    }

    public void updateParam(int w, int h, String outputFile) {
        this.w = w;
        this.h = h;
        this.outputFile = outputFile;
    }

    @Override
    public void doPrepare() {
        if (mMediaRecorder == null) {
            mMediaRecorder = new MediaRecorder();
        }
        mMediaRecorder.reset();
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mMediaRecorder.setVideoEncodingBitRate(VideoUtils.getVideoBitRate(w, h));
        mMediaRecorder.setVideoSize(w, h);
        mMediaRecorder.setVideoFrameRate(30);
        mMediaRecorder.setOutputFile(outputFile);
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
            ALog.dd("prepear ok meida recorder");
        } catch (IOException e) {
            e.printStackTrace();
            EventManager.get().sendMsg(ErrorCode.ERROR_MEDIA_COMMON,
                    "media Recorder doPrepare error: " + e.getMessage());
            ALog.dd("prepare failed media recorder");
        }
    }

    @Override
    public Surface getInputSurface() {
        return mMediaRecorder.getSurface();
    }

    @Override
    public void doStart() {
        mMediaRecorder.start();
    }

    @Override
    public void doStop() {
        mMediaRecorder.stop();
        mMediaRecorder.reset();
    }

    @Override
    public void doRelease() {
        mMediaRecorder.reset();
        mMediaRecorder.release();
        mMediaRecorder = null;
    }

    @Override
    public void drainEncoder(boolean endOfStream) {
// ignore
    }

    @Override
    public long getPTSUs() {
        return System.nanoTime() / 1000L;
    }
}
