package com.zcc.mediarecorder.encoder.video;

import android.view.Surface;

import com.zcc.mediarecorder.common.ILifeCircle;

public interface IVideoEncoderCore extends ILifeCircle {

    public Surface getInputSurface();

    public void drainEncoder(boolean endOfStream);

    public long getPTSUs();

}
