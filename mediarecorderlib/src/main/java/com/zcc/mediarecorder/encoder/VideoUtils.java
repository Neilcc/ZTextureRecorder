package com.zcc.mediarecorder.encoder;

public class VideoUtils {

    private static final int VIDEO_BIT_RATE_IN_KB = 3000;

    public static int getBitRate(int width, int height) {
        // this is magic number from m4m https://github.com/INDExOS/media-for-mobile
        int bitrate = VIDEO_BIT_RATE_IN_KB;
        if (width * height * 30 * 2 * 0.00007 < VIDEO_BIT_RATE_IN_KB) {
            bitrate = (int) (width * height * 30 * 2 * 0.00007);
        }
        return bitrate * 1024;
    }
}
