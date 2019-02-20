package com.zcc.mediarecorder.encoder.utils;

import android.media.MediaCodecInfo;
import android.media.MediaCodecList;

import com.zcc.mediarecorder.ALog;

public class VideoUtils {

    private static final int VIDEO_BIT_RATE_IN_KB = 3000;

    public static int getVideoBitRate(int width, int height) {
        // this is magic number from m4m https://github.com/INDExOS/media-for-mobile
        int bitrate = VIDEO_BIT_RATE_IN_KB;
        if (width * height * 30 * 2 * 0.00007 < VIDEO_BIT_RATE_IN_KB) {
            bitrate = (int) (width * height * 30 * 2 * 0.00007);
        }
        return bitrate * 1024;
    }

    public static MediaCodecInfo selectAudioCodec(final String mimeType) {
        ALog.v("AudioCodec", "selectAudioCodec:\t" + mimeType);
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
                ALog.d("AudioCodec", "supportedType:" + codecInfo.getName() + ",MIME=" + type);
                if (type.equalsIgnoreCase(mimeType)) {
                    result = codecInfo;
                    break LOOP;
                }
            }
        }
        return result;
    }
}
