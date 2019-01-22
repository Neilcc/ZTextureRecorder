package com.zcc.mediarecorder.encoder.video;

import android.view.Surface;

public interface IVideoEncoderCore {

    public Surface getInputSurface();

    public void release();

    public void drainEncoder(boolean endOfStream);

    public void startRecording();

    public long getPTSUs();

    public void prepare();
}
