package com.zcc.mediarecorder.encoder;

import android.media.MediaRecorder;

public class EncoderConfigs {
    public static final int AUDIO_SOURCE = MediaRecorder.AudioSource.MIC;
    // 44.1[KHz] is only setting guaranteed to be available on all devices
    // 采样频率一般共分为22.05KHz、44.1KHz、48KHz三个等级，
    // 22.05KHz只能达到FM广播的声音品质，44.1KHz则是理论上的CD音质界限，48KHz则更加精确一些。
    public static final int AUDIO_SAMPLE_RATE = 44100;
    // 我们常见的16Bit（16比特），可以记录大概96分贝的动态范围。
    // 那么，您可以大概知道，每一个比特大约可以记录6分贝的声音。
    // 同理，20Bit可记录的动态范围大概就是120dB；24Bit就大概是144dB。
    // 假如，我们定义0dB为峰值，那么声音振幅以向下延伸计算，那么，
    // CD音频可的动态范围就是"-96dB～0dB。"，
    // 依次类推，24Bit的HD-Audio高清音频的的动态范围就是"-144dB~0dB。"。
    // 由此可见，位深度较高时，有更大的动态范围可利用，可以记录更低电平的细节。
    public static final int AUDIO_SAMPLE_DEPTH_BIT = 16;
    // WAV一般都是1411kbps
    // lac和ape一般在900-1150之间。是按音乐的性质来的。
    // 电音比特率比传统乐器和清唱大。
    // 同一首歌的flac只有压缩等级0-8的区别（除非是假无损），
    // 比如only my railgun的flac用格式工厂转换后为1175，
    // foobar2000转换压缩等级0是1218。
    public static final int AUDIO_BIT_RATE = AUDIO_SAMPLE_RATE * AUDIO_SAMPLE_DEPTH_BIT;
    // formula :
    //     bitRate = sampleRate * sampleDepth
    //

    public static final int AUDIO_SAMPLE_FRAME_PER_SEC = -1;

    public static final String AUDIO_MIME_TYPE = "audio/mp4a-latm";

}
