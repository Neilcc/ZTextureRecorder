package com.zcc.mediarecorder.demo.utils;

import com.zcc.glrender.grafika.gles.FullFrameRect;
import com.zcc.glrender.grafika.gles.Texture2dProgram;
import com.zcc.lib.IGLRender;

/**
 * Created by cc on 2019-09-19.
 */
public class FullFrameRectGLRender implements IGLRender {
    FullFrameRect mScreenDisplay;
    int mCameraTextureId;

    @Override
    public int createTexture() {
        mScreenDisplay = new FullFrameRect(new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT));
        mCameraTextureId = mScreenDisplay.createTextureObject();
        return mCameraTextureId;
    }

    @Override
    public void rend(int textureId, float[] matrix) {
        mScreenDisplay.drawFrame(textureId, matrix);
    }
}
