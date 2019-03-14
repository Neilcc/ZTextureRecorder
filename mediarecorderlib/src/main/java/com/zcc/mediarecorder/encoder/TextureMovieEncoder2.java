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
import android.support.annotation.UiThread;
import android.view.Surface;

import com.zcc.mediarecorder.ALog;
import com.zcc.mediarecorder.common.ILifeCircle;
import com.zcc.mediarecorder.encoder.core.IMovieEncoderCore;
import com.zcc.mediarecorder.encoder.core.codec.MediaCodecEncoderCore;
import com.zcc.mediarecorder.encoder.core.recorder.MediaRecorderEncoderCore;

import java.io.IOException;

import static com.zcc.mediarecorder.encoder.TextureMovieEncoder2.EncoderType.MEDIA_CODEC;

/**
 * In theory, encoder should be reused in stop to prepare.
 * Let's check it. assume that the source and size do not change during restart.
 * Media recorder and audio record can do this, and MediaCodec can also do it.
 */
public class TextureMovieEncoder2 implements ILifeCircle {
    private static final String TAG = "TextureMovieEncoder2";
    private IMovieEncoderCore mMovieEncoder;

    @UiThread
    public TextureMovieEncoder2(int width, int height, String outputFile, EncoderType encoderType) {
        ALog.d(TAG, "constructor");
        if (encoderType == null) {
            encoderType = MEDIA_CODEC;
        }
        switch (encoderType) {
            case MEDIA_CODEC:
                try {
                    mMovieEncoder = new MediaCodecEncoderCore(width, height, outputFile);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
            case MEDIA_RECORDER:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    mMovieEncoder = new MediaRecorderEncoderCore(width, height, outputFile);

                }
                break;
            default:
                throw new IllegalStateException("unexpected encoderType type");
        }
    }

    public Surface getRecordSurface() {
        ALog.d(TAG, "getRecordSurface");
        return mMovieEncoder.getInputSurface();
    }


    public void onDrawFrame() {
        ALog.d(TAG, "onDrawFrame");
        mMovieEncoder.drainEncoder(false);
    }

    @Override
    public void doStart() {
        ALog.d(TAG, "doStart");
        mMovieEncoder.doStart();
    }

    @Override
    public void doStop() {
        ALog.d(TAG, "doStop");
        mMovieEncoder.drainEncoder(true);
        mMovieEncoder.doStop();
    }

    @Override
    public void doRelease() {
        ALog.d(TAG, "doRelease");
        mMovieEncoder.doRelease();
    }

    @Override
    public void doPrepare() {
        ALog.d(TAG, "doPrepare");
        mMovieEncoder.doPrepare();
    }

    public enum EncoderType {
        MEDIA_RECORDER,
        MEDIA_CODEC
    }

}
